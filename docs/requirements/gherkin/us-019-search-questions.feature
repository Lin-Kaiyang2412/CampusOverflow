# 对应 US-ID: US-019（内容发现/搜索）
@US-019
Feature: 中文问题搜索
  作为用户
  我想按关键词（含中文）搜索问题
  以便快速找到相关内容

  Scenario: 中文关键词命中搜索结果
    Given 系统索引中存在问题"为什么 mvn test 会失败"（含正文"如何排查失败测试"）
    When 我在搜索框输入关键词"排查"并提交
    Then 系统返回搜索结果，第一条为问题"为什么 mvn test 会失败"
    And 结果中标注命中的标题/正文片段

  Scenario: 单字符中文关键词被拒绝
    Given 系统使用 ngram_token_size=2 的中文分词配置
    When 我在搜索框输入单字符关键词"测"并提交
    Then 系统返回 422 Unprocessable Entity，提示关键词过短（至少 2 个字符）
    And 搜索不返回任何结果

  Scenario: 未登录用户也可搜索公开内容
    Given 未登录的访客打开搜索页
    When 访客输入关键词"测试"并提交
    Then 系统返回公开问题的搜索结果（不包含用户私有上下文内容）
