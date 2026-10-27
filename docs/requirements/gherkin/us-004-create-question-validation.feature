# 对应 US-ID: US-004（发布问题校验）
@US-004
Feature: 发布问题校验规则
  作为学生
  我想让系统校验问题输入合法性
  以便避免脏数据进入问答社区

  Scenario: 标签数量越界时拒绝发布
    Given 已登录的学生"林恺洋"已加入课程"软件工程"
    When 我提交问题，标题为"合法的标题文本"，标签数量为 6 个
    Then 系统返回 422 Unprocessable Entity，提示标签数量必须在 1 到 5 之间
    And 问题未被创建

  Scenario: 缺少正文时拒绝发布
    Given 已登录的学生"林恺洋"已加入课程"软件工程"
    When 我提交问题，标题为"合法的标题文本"，正文为空
    Then 系统返回 422 Unprocessable Entity，提示正文不能为空
    And 问题未被创建
