package com.mysqladmin.dto;

import jakarta.validation.constraints.NotBlank;

public record ColumnDefinition(@NotBlank String name, @NotBlank String type, String length, boolean primaryKey, boolean autoIncrement, boolean nullable, String defaultValue, String comment) { }
