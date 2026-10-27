# 对应 US-ID: US-008（采纳答案）
@US-008
Feature: 采纳最佳答案
  作为提问者
  我想采纳一个回答为最佳答案
  以便标记问题已解决并给答主声誉奖励

  Scenario: 提问者成功采纳回答
    Given 已登录的学生"林恺洋"是问题"为什么 mvn test 会失败"的作者
    And 问题"为什么 mvn test 会失败"下存在回答 A1（作者"李四"）
    When 我采纳回答 A1 为最佳答案
    Then 系统返回 200 OK
    And 问题状态变为"已解决"，最佳答案指向 A1
    And 再次采纳同一问题的其他回答被拒绝（每题最多一次且不可更改）
    And 系统发布 AnswerAcceptedEvent，答主"李四"声誉增加 15

  Scenario: 非提问者尝试采纳被拒绝
    Given 已登录的学生"王五"不是问题"为什么 mvn test 会失败"的作者
    When 王五尝试采纳回答 A1 为最佳答案
    Then 系统返回 403 Forbidden，提示只有提问者可以采纳
    And 问题最佳答案保持不变
