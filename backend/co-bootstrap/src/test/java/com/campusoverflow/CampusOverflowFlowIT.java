package com.campusoverflow;

import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.campusoverflow.identity.application.AdminUserService;
import com.campusoverflow.identity.application.CourseService;
import com.campusoverflow.identity.application.CreateCourseCommand;
import com.campusoverflow.identity.application.CreateStaffCommand;
import com.campusoverflow.qa.application.command.PostQuestionCommand;
import com.campusoverflow.qa.application.command.QuestionCommandService;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.Role;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 端到端集成测试（质量场景 R-2 的验收）：注册 → 登录 → 提问 → 回答 → 采纳 → 声誉结算 → 搜索命中 → 收到通知。
 * 使用真实的 MySQL 8 与 Redis 7（Testcontainers），因此也验证了 Flyway 迁移、ngram 全文索引与发件箱中继。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.session.SessionAutoConfiguration",
        "co.outbox.poll-interval-ms=200",
        "spring.flyway.clean-disabled=false"
})
class CampusOverflowFlowIT {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.39"))
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci",
                    "--ngram-token-size=2");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    AdminUserService admins;

    @Autowired
    CourseService courses;

    @Autowired
    QuestionCommandService questions;

    @Autowired
    JdbcTemplate jdbc;

    /**
     * PR #37 评审要求：真实 MySQL 上验证「同一问题并发发布悬赏只有一个成功、失败请求不扣积分」。
     * 两个线程同时 POST 同一问题的悬赏；无论请求是否真正交错，最终都必须满足：
     * 一个 201 一个 409、数据库只有一条 OPEN 悬赏、积分只冻结一次。
     */
    @Test
    void 同一问题并发发布悬赏只有一个成功且失败请求不扣积分() throws Exception {
        // 用虚拟管理员引导教师账号（与 DemoDataSeeder 相同的引导方式，仅用于测试造数）
        Actor system = new Actor(0L, "system", Role.ADMIN);
        long teacherId = admins.createStaff(system, new CreateStaffCommand(
                "T9001", "并发王老师", "it-bounty-teacher@campus.edu.cn", "Campus2026", Role.TEACHER, "计算机工程学院"));
        Actor teacher = new Actor(teacherId, "并发王老师", Role.TEACHER);
        MockHttpSession teacherSession = login("T9001");
        long courseId = courses.create(teacher, new CreateCourseCommand("CS9001", "并发悬赏课", "2026-秋"));
        long questionId = questions.post(teacher, new PostQuestionCommand(courseId,
                "同一问题能否被并发发起两次悬赏？",
                "两个请求同时发布同一问题的悬赏，系统必须只创建一条进行中的悬赏，且失败方不得扣减任何积分。",
                List.of("并发")));

        // 教师声誉账户由 UserRegisteredEvent 异步创建，等初始积分到账再发悬赏
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                mvc.perform(get("/api/v1/users/" + teacherId + "/reputation").session(teacherSession))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.availablePoints").value(1000)));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch fire = new CountDownLatch(1);
            Callable<Integer> openBounty = () -> {
                ready.countDown();
                fire.await(10, TimeUnit.SECONDS);
                return mvc.perform(post("/api/v1/questions/" + questionId + "/bounty")
                                .session(teacherSession).with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"points\":50,\"days\":7}"))
                        .andReturn().getResponse().getStatus();
            };
            Future<Integer> first = pool.submit(openBounty);
            Future<Integer> second = pool.submit(openBounty);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            fire.countDown();

            assertThat(List.of(first.get(60, TimeUnit.SECONDS), second.get(60, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        } finally {
            pool.shutdownNow();
        }

        // 数据库层面只有一条 OPEN 悬赏；失败请求的冻结已回滚，积分只扣了一次 50。
        Integer openCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM rep_bounty WHERE question_id = ? AND status = 'OPEN'",
                Integer.class, questionId);
        assertThat(openCount).isEqualTo(1);
        mvc.perform(get("/api/v1/users/" + teacherId + "/reputation").session(teacherSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availablePoints").value(950))
                .andExpect(jsonPath("$.frozenPoints").value(50));
    }

    @Test
    void 提问到采纳的完整链路会异步结算声誉并更新搜索与通知() throws Exception {
        register("2026001", "测试小明", "it-ming@campus.edu.cn");
        long hongId = register("2026002", "测试小红", "it-hong@campus.edu.cn");
        MockHttpSession ming = login("2026001");
        MockHttpSession hong = login("2026002");

        long questionId = idOf(mvc.perform(post("/api/v1/questions").session(ming).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"跳表和红黑树在工程上如何取舍？",
                                 "body":"课程项目里需要一个有序结构，二者的实现复杂度和并发友好程度差别很大，应该怎么选？",
                                 "tags":["数据结构","跳表"]}
                                """))
                .andExpect(status().isCreated()).andReturn());

        long answerId = idOf(mvc.perform(post("/api/v1/questions/" + questionId + "/answers").session(hong).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body":"并发场景优先跳表：它的插入删除只需局部指针操作，容易做成无锁结构；单线程内存场景红黑树更省内存。"}
                                """))
                .andExpect(status().isCreated()).andReturn());

        mvc.perform(put("/api/v1/questions/" + questionId + "/accepted-answer").session(ming).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answerId\":" + answerId + "}"))
                .andExpect(status().isNoContent());

        // 采纳后：回答者 +15 声誉（经发件箱异步结算）
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                mvc.perform(get("/api/v1/users/" + hongId + "/reputation"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.reputation").value(15))
                        .andExpect(jsonPath("$.badges[?(@.code=='FIRST_ACCEPTED')]").exists()));

        // 搜索投影：中文关键词可命中，且标记为已采纳
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                mvc.perform(get("/api/v1/search/questions").param("q", "跳表"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.items[0].id").value(questionId))
                        .andExpect(jsonPath("$.items[0].accepted").value(true)));

        // 通知中心：回答者收到“被采纳”通知
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                mvc.perform(get("/api/v1/notifications").session(hong))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.items[?(@.type=='ANSWER_ACCEPTED')]").exists()));
    }

    @Test
    void 不能给自己的内容投票() throws Exception {
        register("2026003", "测试小刚", "it-gang@campus.edu.cn");
        MockHttpSession gang = login("2026003");
        long questionId = idOf(mvc.perform(post("/api/v1/questions").session(gang).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"为什么哈希表的负载因子一般取 0.75？",
                                 "body":"JDK HashMap 默认负载因子是 0.75，这个取值是怎么权衡空间与冲突概率得到的？",
                                 "tags":["数据结构"]}
                                """))
                .andExpect(status().isCreated()).andReturn());

        mvc.perform(put("/api/v1/votes").session(gang).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"QUESTION\",\"targetId\":" + questionId + ",\"value\":1}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("QA-1015"));
    }

    @Test
    void 未登录不能提问且缺少CSRF令牌会被拒绝() throws Exception {
        mvc.perform(post("/api/v1/questions").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"未登录的提问测试标题\",\"body\":\"这是一段足够长的正文用于通过参数校验。\",\"tags\":[\"测试\"]}"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"2026001\",\"password\":\"Campus2026\"}"))
                .andExpect(status().isForbidden());
    }

    private long register(String username, String displayName, String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new RegisterPayload(username, displayName, email,
                                "Campus2026", "计算机工程学院", "计科2301"))))
                .andExpect(status().isCreated()).andReturn();
        return idOf(result);
    }

    private MockHttpSession login(String username) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"Campus2026\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private long idOf(MvcResult result) throws Exception {
        JsonNode node = json.readTree(result.getResponse().getContentAsString());
        return node.get("id").asLong();
    }

    private record RegisterPayload(String username, String displayName, String email, String password,
                                   String college, String className) {
    }
}
