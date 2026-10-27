# 对应 US-ID: US-008（采纳答案 - 状态约束）
@US-008
Feature: 采纳答案状态约束
  作为提问者
  我想确保采纳规则不被重复或误操作破坏
  以便业务规则一致

  Scenario: 同一问题重复采纳被拒绝
    Given 问题"为什么 mvn test 会失败"已有最佳答案 A1
    When 提问者尝试采纳回答 A2 为最佳答案
    Then 系统返回 409 Conflict，提示每题只能采纳一次且不可更改
    And 最佳答案仍为 A1

  Scenario: 采纳已软删除的回答被拒绝
    Given 回答 A3 已被作者软删除（deletedAt 非空）
    When 提问者尝试采纳 A3 为最佳答案
    Then 系统返回 404 Not Found，提示回答不存在
    And 问题最佳答案保持不变
