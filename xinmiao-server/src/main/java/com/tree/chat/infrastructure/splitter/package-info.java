/**
 * 聊天/RAG 上下文中「知识入库前的文本切分」——<b>基础设施层（适配器实现）</b>。
 * <p>
 * <b>在本项目里 DDD 怎么体现：</b>
 * <ul>
 *   <li><b>领域层</b>（{@code com.tree.chat.domain}）定义端口
 *       {@link com.tree.chat.domain.port.NocodeDocumentSplitStrategy} 与来源标识
 *       {@link com.tree.chat.domain.model.NocodeStructuredSource}：说明「有哪些文档类型、策略对接口长什么样」，
 *      不写 Spring、不写正则细节。</li>
 *   <li><b>本包</b>提供端口的具体实现（各类 {@code TextSplitter}、{@code @Component} 策略、注册表），
 *       对应六边形架构里的 <b> Driven Adapter（出站适配器）</b>：把领域需要的「切块」能力落到算法与框架上。</li>
 * </ul>
 * 若不需要包级说明，可删除本文件，不影响编译与运行。
 * <p>
 * 子包：{@code chinese} — 通用滑窗；{@code nocode} — {@code classpath:nocode/*.txt} 专用策略与注册。
 */
package com.tree.chat.infrastructure.splitter;
