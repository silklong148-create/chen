package com.mysqladmin.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.Map;

public record RowCreateRequest(@NotEmpty Map<String, Object> values) { }
