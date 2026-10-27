# 对应 US-ID: US-007（回答问题）
@US-007
Feature: 回答问题
  作为学生
  我想回答一个问题
  以便帮助提问者解决问题

  Scenario: 学生成功回答问题
    Given 已登录的学生"李四"正在浏览问题"为什么 mvn test 会失败"
    When 我提交回答，正文为"检查 test 目录下的断言与 TODO 方法实现"
    Then 系统返回 201 Created
    And 该回答出现在问题详情页的回答列表中
    And 问题回答数加 1
    And 系统发布 AnswerSubmittedEvent 事件供下游订阅

  Scenario: 对已软删除的问题回答被拒绝
    Given 问题"为什么 mvn test 会失败"已被作者软删除（deletedAt 非空）
    When 已登录的学生"李四"提交回答
    Then 系统返回 409 Conflict，提示问题不存在或不可回答
