package edu.wpi.cs528finalproject.ui.profile

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import edu.wpi.cs528finalproject.LoginActivity
import edu.wpi.cs528finalproject.R
import kotlinx.android.synthetic.main.fragment_profile.*
import kotlin.math.roundToInt

class ProfileFragment : Fragment() {

    private lateinit var profileViewModel: ProfileViewModel

    private var correctlyWearingMaskCounter = 0L
    private var numberOfTimesPromptedToWearMask = 0L

    private lateinit var database: DatabaseReference

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        profileViewModel =
            ViewModelProvider(this).get(ProfileViewModel::class.java)
        val root = inflater.inflate(R.layout.fragment_profile, container, false)
//        val textView: TextView = root.findViewById(R.id.text_dashboard)
//        dashboardViewModel.text.observe(viewLifecycleOwner, Observer {
//            textView.text = it
//        })
        val signoutButton = root.findViewById<Button>(R.id.button)
        val updatePasswordButton = root.findViewById<Button>(R.id.button4)
        database = Firebase.database.reference

        signoutButton.setOnClickListener {
            SignOutUser()
        }

        updatePasswordButton.setOnClickListener{
            UpdatePassword()
        }

        // Calculate the percentage of times the user wears his or her mask based on the data in firebase
        val currentFirebaseUser = FirebaseAuth.getInstance().currentUser?.email?.split('@')?.get(0)
            ?: "No User"
        var percentage = 0.0
        val percentEventListener = object : ValueEventListener {
            override fun onCancelled(databaseError: DatabaseError) {
                // handle error
            }

            override fun onDataChange(dataSnapshot: DataSnapshot) {
                correctlyWearingMaskCounter =
                    (dataSnapshot.child("correctlyWearingMaskCounter").value ?: 0L) as Long
                numberOfTimesPromptedToWearMask =
                    (dataSnapshot.child("numberOfTimesPromptedToWearMask").value
                        ?: 0L) as Long
                //update the view with the percent of data
                if (numberOfTimesPromptedToWearMask != 0L) {
                    percentage =
                        (correctlyWearingMaskCounter.toDouble() / numberOfTimesPromptedToWearMask.toDouble())
                    statsText.text = getString(R.string.statsSentence).format(percentage * 100)
                } else {
                    statsText.text = getString(R.string.noDataSentence)
                }
            }
        }
        val ref = database.child("maskWearing").child(currentFirebaseUser)
        ref.addListenerForSingleValueEvent(percentEventListener)

        // Calculate the users percentile compared to all other users in firebase
        val percentileArray = FloatArray(10)
        val percentileEventListener = object : ValueEventListener {
            override fun onCancelled(databaseError: DatabaseError) {
                // handle error
            }

            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val data = dataSnapshot.value
                if (data !is HashMap<*, *>) return
                // determine the percentile of the user
                // loop through all user percentages and determine how many this user is larger than
                // round up to make it a nicer number to display
                val percentageList = DoubleArray(data.size)
                var counter = 0
                for((_,value) in data){
                    if (value !is HashMap<*, *>) return
                    val numerator = (value["correctlyWearingMaskCounter"] as Long).toDouble()
                    val denominator =
                        (value["numberOfTimesPromptedToWearMask"] as Long).toDouble()
                    percentageList[counter] = numerator / denominator
                    when(percentageList[counter]*100){
                        in 0.0..10.0 -> percentileArray[0]++
                        in 11.0..20.0 -> percentileArray[1]++
                        in 21.0..30.0 -> percentileArray[2]++
                        in 31.0..40.0 -> percentileArray[3]++
                        in 41.0..50.0 -> percentileArray[4]++
                        in 51.0..60.0 -> percentileArray[5]++
                        in 61.0..70.0 -> percentileArray[6]++
                        in 71.0..80.0 -> percentileArray[7]++
                        in 81.0..90.0 -> percentileArray[8]++
                        in 91.0..100.0 -> percentileArray[9]++
                    }
                    counter++
                }
                counter = 0
                percentageList.sort()
                for (p in percentageList) {
                    if (percentage < p) {
                        break
                    }
                    counter++
                }
                val percentile = ((counter.toDouble() / data.size.toDouble()) * 100).roundToInt()
                when {
                    percentile >= 75 -> {
                        percentileCircle.backgroundTintList = ColorStateList.valueOf(
                            ContextCompat.getColor(requireContext(),
                                R.color.green
                            )
                        )
                    }
                    percentile > 40 -> {
                        percentileCircle.backgroundTintList = ColorStateList.valueOf(
                            ContextCompat.getColor(requireContext(),
                                R.color.yellow
                            )
                        )
                    }
                    else -> {
                        percentileCircle.backgroundTintList = ColorStateList.valueOf(
                            ContextCompat.getColor(requireContext(),
                                R.color.red
                            )
                        )
                    }
                }
                percentileText.text = getString(R.string.percentile).format(percentile)
                percentileCircle.text = getString(R.string.percentileNum).format(percentile)

                setUpBarChart(percentileArray)
            }
        }
        val ref2 = database.child("maskWearing")
        ref2.addListenerForSingleValueEvent(percentileEventListener)

        return root
    }

    private fun SignOutUser() {
        val intent = Intent(activity, LoginActivity::class.java)
        FirebaseAuth.getInstance().signOut();
        startActivity(intent)
    }

    private fun UpdatePassword(){
        val usernewPassword = view?.findViewById<EditText>(R.id.passworChangeInput)?.text.toString()
        val user = Firebase.auth.currentUser
        
        user!!.updatePassword(usernewPassword).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Toast.makeText(activity, "Update Password Successful!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(activity, "Something Went Wrong  !", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setUpBarChart(percentileArray: FloatArray){
        val barEntries = arrayListOf<BarEntry>()
        val xAxisLabels = arrayListOf<String>()
        xAxisLabels.addAll(listOf("0-10%","11-20%","21-30%","31-40%","41-50%","51-60%","61-70%","71-80%","81-90%","91-100%"))
        for((index, p) in percentileArray.withIndex()){
            barEntries.add(BarEntry(p, index))
        }
        val barDataSetY = BarDataSet(barEntries,"Number of Users")
        barDataSetY.color = ContextCompat.getColor(requireContext(), R.color.red)
        val barData = BarData(xAxisLabels, barDataSetY)
        barChart.data = barData
        barChart.invalidate()
        barChart.setScaleEnabled(false)
        barChart.setDescription("")
        barChart.xAxis.setLabelsToSkip(0)
        barChart.xAxis.labelRotationAngle = -30f
        barChart.extraTopOffset = 18f
    }
}