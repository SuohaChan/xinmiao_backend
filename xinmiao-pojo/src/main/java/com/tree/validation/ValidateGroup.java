package com.tree.validation;

import jakarta.validation.groups.Default;

/**
 * 校验组：仅身份验证时校验（如 StudentInfo 的 idNumber、admissionNumber 必填）。
 */
public interface ValidateGroup extends Default {
}
