package com.example.smsverificationapiexample;

/**
 * @author Nowshad Hasan
 * @created on 2/4/2020 5:28 PM
 */
public interface SMSListener {
    void onSuccess(String message);

    void onError(String message);
}
