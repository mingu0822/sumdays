package com.example.sumdays.network

import com.example.sumdays.network.apiService.ApiService
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class RetrofitClientTest {

    @Test
    fun authApiService_isNotNull_andIsSingleton() {
        val instance1 = ApiClient.api
        val instance2 = ApiClient.api

        assertThat(instance1 === instance2, `is`(true))
    }

    @Test
    fun authApiService_isProxyImplementingAuthApiService() {
        val instance = ApiClient.api
        val interfaces = instance.javaClass.interfaces.map { it.name }

        assertThat(interfaces.contains(ApiService::class.java.name), `is`(true))
    }
}
