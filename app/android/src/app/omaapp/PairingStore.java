package app.omaapp;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

final class PairingStore {
    private static final String KEY_ALIAS = "app.omaapp.pairing.aes";
    private static final String ENCRYPTED_CREDENTIAL = "credential_ciphertext";
    private static final String CREDENTIAL_IV = "credential_iv";
    private static final String PENDING_SCAN_CIPHERTEXT = "pending_scan_ciphertext";
    private static final String PENDING_SCAN_IV = "pending_scan_iv";
    private static final String DEVICE_ID = "device_id";
    private static final int GCM_TAG_BITS = 128;

    private PairingStore() {}

    static synchronized String deviceId(Context context) {
        SharedPreferences preferences = preferences(context);
        String id = preferences.getString(DEVICE_ID, "");
        if (id.isEmpty()) {
            id = UUID.randomUUID().toString();
            preferences.edit().putString(DEVICE_ID, id).apply();
        }
        return id;
    }

    static void writeCredential(Context context, JSONObject credential) throws Exception {
        byte[] plaintext = credential.toString().getBytes(StandardCharsets.UTF_8);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        byte[] ciphertext = cipher.doFinal(plaintext);
        preferences(context).edit()
                .putString(ENCRYPTED_CREDENTIAL, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                .putString(CREDENTIAL_IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .apply();
    }

    @Nullable
    static JSONObject readCredential(Context context) {
        SharedPreferences preferences = preferences(context);
        String encrypted = preferences.getString(ENCRYPTED_CREDENTIAL, "");
        String iv = preferences.getString(CREDENTIAL_IV, "");
        if (encrypted.isEmpty() || iv.isEmpty()) {
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(),
                    new GCMParameterSpec(GCM_TAG_BITS, Base64.decode(iv, Base64.NO_WRAP)));
            byte[] plaintext = cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP));
            return new JSONObject(new String(plaintext, StandardCharsets.UTF_8));
        } catch (Exception error) {
            clearCredential(context);
            preferences.edit().remove(DEVICE_ID).apply();
            return null;
        }
    }

    static void clearCredential(Context context) {
        preferences(context).edit()
                .remove(ENCRYPTED_CREDENTIAL)
                .remove(CREDENTIAL_IV)
                .apply();
    }

    static synchronized boolean writePendingScan(Context context, String value) {
        if (value == null || value.isEmpty() || value.length() > 2048) {
            return false;
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
            byte[] ciphertext = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            preferences(context).edit()
                    .putString(PENDING_SCAN_CIPHERTEXT,
                            Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                    .putString(PENDING_SCAN_IV,
                            Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                    .apply();
            return true;
        } catch (Exception error) {
            clearPendingScan(context);
            return false;
        }
    }

    static synchronized String consumePendingScan(Context context) {
        SharedPreferences preferences = preferences(context);
        String encrypted = preferences.getString(PENDING_SCAN_CIPHERTEXT, "");
        String iv = preferences.getString(PENDING_SCAN_IV, "");
        clearPendingScan(context);
        if (encrypted.isEmpty() || iv.isEmpty()) {
            return "";
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(),
                    new GCMParameterSpec(GCM_TAG_BITS, Base64.decode(iv, Base64.NO_WRAP)));
            byte[] plaintext = cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP));
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception error) {
            return "";
        }
    }

    private static void clearPendingScan(Context context) {
        preferences(context).edit()
                .remove(PENDING_SCAN_CIPHERTEXT)
                .remove(PENDING_SCAN_IV)
                .apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(
                PairingBridge.PREFERENCES, Context.MODE_PRIVATE);
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        }
        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }
}
