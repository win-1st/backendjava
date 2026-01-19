package com.tathang.example304.payload.request;

import lombok.Data;

@Data
public class ResetPasswordOtpRequest {
    private String email;
    private String otp;
    private String newPassword;
    // getter setter
}
