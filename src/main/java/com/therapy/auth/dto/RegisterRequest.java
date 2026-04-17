package com.therapy.auth.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;

@Data
public class RegisterRequest {

    @NotBlank
    @Email(message = "Email inválido")
    private String email;

    @NotBlank
    @Size(min = 8, max = 100, message = "La contraseña debe tener entre 8 y 100 caracteres")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
        message = "La contraseña debe tener al menos una mayúscula, una minúscula y un número"
    )
    private String password;

    @NotBlank
    @Size(min = 2, max = 255, message = "El nombre debe tener entre 2 y 255 caracteres")
    private String fullName;

    @NotNull(message = "La fecha de nacimiento es obligatoria")
    private LocalDate dateOfBirth;

    @Size(max = 50)
    private String phone;

    @Pattern(regexp = "^[A-Z]{2}$", message = "Código de país inválido")
    private String countryCode = "AR";
}
