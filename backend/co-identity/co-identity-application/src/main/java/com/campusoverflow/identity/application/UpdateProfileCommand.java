package com.campusoverflow.identity.application;

/** 本人可编辑的资料字段；学号、角色与认证状态不在此命令中。 */
public record UpdateProfileCommand(String displayName, String email, String college, String className) {
}
