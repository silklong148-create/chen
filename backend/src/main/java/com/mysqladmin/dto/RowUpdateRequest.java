package com.mysqladmin.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.Map;

public record RowUpdateRequest(@NotEmpty Map<String, Object> key, @NotEmpty Map<String, Object> values) { }
