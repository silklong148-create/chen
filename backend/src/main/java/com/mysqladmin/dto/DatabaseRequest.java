package com.mysqladmin.dto;

import jakarta.validation.constraints.NotBlank;

public record DatabaseRequest(@NotBlank String name, String charset, String collation) { }
