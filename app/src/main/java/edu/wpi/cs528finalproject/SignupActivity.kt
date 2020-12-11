package edu.wpi.cs528finalproject

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

class SignupActivity : AppCompatActivity() {
    private lateinit var database: DatabaseReference


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)
        database = Firebase.database.reference

        val signupbutton = findViewById<Button>(R.id.button2)
        val loginbutton = findViewById<TextView>(R.id.LOGIN)

        loginbutton.setOnClickListener{
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }

        signupbutton.setOnClickListener {
            RegisterUser()
        }
    }

    private fun RegisterUser() {
        val useremail = findViewById<EditText>(R.id.emailinput).text.toString()
        val userfullname = findViewById<EditText>(R.id.fullnameinput).text.toString()
        val userpassword = findViewById<EditText>(R.id.passwordinput).text.toString()
        val usercpassword = findViewById<EditText>(R.id.cpasswordinput).text.toString()

        if (useremail.isEmpty() || userpassword.isEmpty() || userfullname.isEmpty()) {
            Toast.makeText(this, "One of the above fields is empty !", Toast.LENGTH_SHORT).show()
            return
        }

        if (!(userpassword.equals(usercpassword))){
            Toast.makeText(this, "Password and Confirm Password do not match !", Toast.LENGTH_SHORT).show()
            return
        }

        FirebaseAuth.getInstance().createUserWithEmailAndPassword(useremail, userpassword).addOnCompleteListener {
            if(!it.isSuccessful) return@addOnCompleteListener

            database.child("users").child(it.result?.user?.uid.toString()).child("username").setValue(userfullname)
            database.child("users").child(it.result?.user?.uid.toString()).child("useremail").setValue(useremail)
            Log.d("Main", "Successfully created with uid: ${it.result?.user?.uid}")
        }.addOnFailureListener {
            Toast.makeText(this, "Could not sign up : ${it.message}", Toast.LENGTH_SHORT).show()
        }.addOnSuccessListener {
            val intent = Intent(this, NavigationActivity::class.java)
            startActivity(intent)
        }
    }

}