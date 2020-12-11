package edu.wpi.cs528finalproject

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class LoginActivity: AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val auth = FirebaseAuth.getInstance();

        if (auth.currentUser != null ){
            val intent = Intent(this, NavigationActivity::class.java)
            startActivity(intent)
        }

        setContentView(R.layout.activity_login)
        val signupbutton1 = findViewById<TextView>(R.id.terms1)
        val signupbutton2 = findViewById<TextView>(R.id.terms2)
        val loginButton = findViewById<Button>(R.id.button2)

        signupbutton1.setOnClickListener{
            backToSignup()
        }
        signupbutton2.setOnClickListener{
            backToSignup()
        }
        loginButton.setOnClickListener{
            loginUser()
        }
    }

    private fun backToSignup(){
        val intent = Intent(this, SignupActivity::class.java)
        startActivity(intent)
    }

    private fun loginUser(){
        val useremail = findViewById<EditText>(R.id.fullnameinput).text.toString()
        val userpassword = findViewById<EditText>(R.id.passwordinput).text.toString()

        if (useremail.isEmpty() || userpassword.isEmpty()) {
            Toast.makeText(this, "Email or Password is Empty !", Toast.LENGTH_SHORT).show()
            return
        }

        FirebaseAuth.getInstance().signInWithEmailAndPassword(useremail, userpassword)
                .addOnCompleteListener{
                    if(!it.isSuccessful) return@addOnCompleteListener
                    Toast.makeText(this, "Log In Successful !", Toast.LENGTH_SHORT).show()
                }.addOnFailureListener {
                    Toast.makeText(this, "Could not log in: ${it.message}", Toast.LENGTH_SHORT).show()
                }.addOnSuccessListener {
                    val intent = Intent(this, NavigationActivity::class.java)
                    startActivity(intent)
                }

    }


}