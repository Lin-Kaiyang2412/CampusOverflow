# 对应 US-ID: US-004（发布问题）
@US-004
Feature: 发布问题
  作为学生
  我想发布包含标题、Markdown 正文和标签的问题
  以便获得同学解答

  Scenario: 学生成功发布带标签的问题
    Given 已登录的学生"林恺洋"已加入课程"软件工程"
    When 我提交问题，标题为"为什么 mvn test 会失败"（15 字符）、正文为 Markdown 内容"如何排查 38 个失败测试"，并选择 2 个标签 ["maven", "测试"]
    Then 系统返回 201 Created
    And 问题出现在问题列表第一条，显示作者"林恺洋"与 2 个标签
    And 其他用户可查看该问题详情

  Scenario: 标题长度越界时拒绝发布
    Given 已登录的学生"林恺洋"已加入课程"软件工程"
    When 我提交问题，标题长度为 9 字符（低于最小 10）
    Then 系统返回 422 Unprocessable Entity，携带业务错误码
    And 问题列表不包含该问题
