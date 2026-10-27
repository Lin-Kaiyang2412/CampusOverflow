# 对应 US-ID: US-010（评论）
@US-010
Feature: 发表评论
  作为用户
  我想在问题或回答下发表评论
  以便补充说明或讨论细节

  Scenario: 用户在回答下发表评论
    Given 已登录的学生"赵六"正在查看回答 A1（属于问题"为什么 mvn test 会失败"）
    When 我在回答 A1 下发表评论"补充：建议同时查看 pom.xml 配置"
    Then 系统返回 201 Created
    And 该评论出现在回答 A1 的评论区
    And 系统发布 CommentPostedEvent 事件

  Scenario: 评论回复超过一层被拒绝
    Given 评论 C1 已挂在回答 A1 下（第一层）
    When 我尝试在评论 C1 下再回复评论 C2
    Then 系统返回 422 Unprocessable Entity，提示评论最多只支持一层回复
    And 评论 C2 未被创建
