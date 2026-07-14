package com.mysqladmin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record TableRequest(@NotBlank String name, String comment, @NotEmpty List<@Valid ColumnDefinition> columns) { }
