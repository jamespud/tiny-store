package com.github.spud.tinystore.account.infrastructure.audit;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "audit_log")
@Comment("操作审计日志")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("日志ID")
    private Long id;

    @Column(nullable = false, length = 255)
    @Comment("请求URI")
    private String requestUri;

    @Column(nullable = false, length = 10)
    @Comment("HTTP方法")
    private String httpMethod;

    @Column(length = 64)
    @Comment("客户端IP")
    private String clientIp;

    @Column(length = 512)
    @Comment("用户代理")
    private String userAgent;

    @Column(columnDefinition = "TEXT")
    @Comment("请求参数")
    private String requestParams;

    @Column(nullable = false)
    @Comment("是否成功")
    private Boolean success;

    @Column(columnDefinition = "TEXT")
    @Comment("错误信息")
    private String errorMessage;

    @Column(nullable = false, columnDefinition = "TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP")
    @Comment("创建时间")
    private LocalDateTime createTime;
}