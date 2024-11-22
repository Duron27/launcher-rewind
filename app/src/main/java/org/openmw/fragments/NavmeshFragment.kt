package org.openmw.fragments

import android.os.Bundle
import org.libsdl.app.SDLActivity
import org.openmw.OPENMW_NAVMESH_LIB
import org.openmw.jniLibsArrayNavmesh
import org.openmw.utils.LogCat

class NavmeshActivity : SDLActivity() {

    override fun getLibraries(): Array<String> {
        return jniLibsArrayNavmesh
    }

    override fun getMainSharedObject(): String {
        return OPENMW_NAVMESH_LIB
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create an instance of LogCat and call enableLogcat
        val logCat = LogCat(this)
        logCat.enableLogcat()

    }
}
