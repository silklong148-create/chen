package com.mysqladmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record IndexRequest(@NotBlank String name, boolean unique, @NotEmpty List<@NotBlank String> columns) { }
