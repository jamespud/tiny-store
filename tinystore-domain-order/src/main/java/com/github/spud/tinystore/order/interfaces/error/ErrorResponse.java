package com.github.spud.tinystore.order.interfaces.error;

import java.time.OffsetDateTime;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    private String code;
    private String message;
    private Map<String, Object> details;
    private OffsetDateTime timestamp = OffsetDateTime.now();
}
