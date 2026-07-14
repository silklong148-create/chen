package com.mysqladmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ViewRequest(@NotBlank String name, @NotBlank @Size(max = 10000) String selectSql) { }
