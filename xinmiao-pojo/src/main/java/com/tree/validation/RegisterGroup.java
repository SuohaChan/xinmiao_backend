package com.tree.validation;

import jakarta.validation.groups.Default;

/**
 * 校验组：仅注册时校验（如 Counselor 的 username、password 必填，更新时可能不传 password）。
 */
public interface RegisterGroup extends Default {
}
