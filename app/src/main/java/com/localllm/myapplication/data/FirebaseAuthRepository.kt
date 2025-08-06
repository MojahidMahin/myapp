package com.localllm.myapplication.data

import android.app.Activity
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.android.gms.common.api.ApiException
import com.localllm.myapplication.R

class FirebaseAuthRepository(private val activity: Activity) {

    private val auth = FirebaseAuth.getInstance()
    private val RC_SIGN_IN = 9001
    private val RC_CHANGE_EMAIL = 9002

    fun addAuthStateListener(onAuthStateChanged: (String?) -> Unit) {
        auth.addAuthStateListener { firebaseAuth ->
            val currentUser = firebaseAuth.currentUser
            onAuthStateChanged(currentUser?.email)
        }
    }

    fun signIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(activity.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        val googleSignInClient = GoogleSignIn.getClient(activity, gso)

        // Sign out first to force account picker
        googleSignInClient.signOut().addOnCompleteListener {
            val signInIntent = googleSignInClient.signInIntent
            activity.startActivityForResult(signInIntent, RC_SIGN_IN)
        }
    }

    fun handleSignInResult(data: Intent?, onSuccess: () -> Unit) {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account = task.getResult(ApiException::class.java)
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            auth.signInWithCredential(credential)
                .addOnCompleteListener(activity) { task ->
                    if (task.isSuccessful) {
                        onSuccess()
                    } else {
                        Log.e("SignIn", "Failure", task.exception)
                    }
                }
        } catch (e: Exception) {
            Log.e("SignIn", "Google sign in failed", e)
        }
    }

    fun signOut() {
        auth.signOut()
    }

    fun changeEmailWithOAuth() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(activity.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        val googleSignInClient = GoogleSignIn.getClient(activity, gso)
        
        // Force account picker for email change
        googleSignInClient.signOut().addOnCompleteListener {
            val signInIntent = googleSignInClient.signInIntent
            activity.startActivityForResult(signInIntent, RC_CHANGE_EMAIL)
        }
    }

    fun handleChangeEmailResult(data: Intent?, onSuccess: () -> Unit, onError: (Exception?) -> Unit) {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val newAccount = task.getResult(ApiException::class.java)
            val credential = GoogleAuthProvider.getCredential(newAccount.idToken, null)
            
            // Re-authenticate with new account
            auth.signInWithCredential(credential)
                .addOnCompleteListener(activity) { authTask ->
                    if (authTask.isSuccessful) {
                        Log.d("ChangeEmail", "Email changed successfully via OAuth")
                        onSuccess()
                    } else {
                        Log.e("ChangeEmail", "OAuth email change failed", authTask.exception)
                        onError(authTask.exception)
                    }
                }
        } catch (e: Exception) {
            Log.e("ChangeEmail", "OAuth email change failed", e)
            onError(e)
        }
    }

    fun changeEmail(newEmail: String, onSuccess: () -> Unit, onError: (Exception?) -> Unit) {
        val user = auth.currentUser
        val account = GoogleSignIn.getLastSignedInAccount(activity)

        if (user != null && account != null && account.idToken != null) {
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            user.reauthenticate(credential).addOnCompleteListener { authTask ->
                if (authTask.isSuccessful) {
                    user.updateEmail(newEmail).addOnCompleteListener { updateTask ->
                        if (updateTask.isSuccessful) {
                            onSuccess()
                        } else {
                            onError(updateTask.exception)
                        }
                    }
                } else {
                    onError(authTask.exception)
                }
            }
        } else {
            onError(Exception("Missing user or account token"))
        }
    }

}
