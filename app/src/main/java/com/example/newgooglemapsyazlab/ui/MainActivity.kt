package com.example.newgooglemapsyazlab.ui

import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import com.example.newgooglemapsyazlab.R
import com.example.newgooglemapsyazlab.Util.Permissions.hasLocationPermission
import com.example.newgooglemapsyazlab.Util.Permissions.hasPostNotificationsPermission

class MainActivity : AppCompatActivity() {

    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        navController = navHostFragment.navController

        if (hasLocationPermission(this)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (hasPostNotificationsPermission(this)) {
                    navController.navigate(R.id.action_permissionFragment_to_mapsFragment)
                } else {
                    navController.navigate(R.id.permissionFragment)
                }
            } else {
                navController.navigate(R.id.action_permissionFragment_to_mapsFragment)
            }
        }

    }
}
