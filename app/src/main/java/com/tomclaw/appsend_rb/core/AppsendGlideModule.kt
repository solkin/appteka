package com.tomclaw.appsend_rb.core

import android.content.Context
import android.content.pm.PackageInfo
import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.module.AppGlideModule
import com.tomclaw.appsend_rb.util.PackageIconGlideLoader
import java.io.InputStream

/**
 * Created by solkin on 21/01/2018.
 */
@GlideModule
class AppsendGlideModule : AppGlideModule() {

    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        registry.append(
                PackageInfo::class.java,
                InputStream::class.java,
                object : ModelLoaderFactory<PackageInfo, InputStream> {

                    override fun build(multiFactory: MultiModelLoaderFactory) =
                            PackageIconGlideLoader(context.packageManager)

                    override fun teardown() {}

                }
        )
    }

}