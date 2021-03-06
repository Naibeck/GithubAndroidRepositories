package com.valeserber.githubchallenge.repository

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations
import androidx.paging.DataSource
import androidx.paging.LivePagedListBuilder
import com.valeserber.githubchallenge.database.DBOwner
import com.valeserber.githubchallenge.database.DBRepository
import com.valeserber.githubchallenge.database.GithubDatabase
import com.valeserber.githubchallenge.database.asDomainModel
import com.valeserber.githubchallenge.domain.GithubSearchResult
import com.valeserber.githubchallenge.domain.Owner
import com.valeserber.githubchallenge.domain.Repository
import com.valeserber.githubchallenge.network.GithubApiService
import kotlinx.coroutines.*


class GithubSearchRepository(
    private val database: GithubDatabase,
    private val apiService: GithubApiService
) {

    var criteria: String = "stars"

    private suspend fun getOwnerById(id: Long): LiveData<DBOwner> {
        return withContext(Dispatchers.IO) {
            return@withContext database.githubRepositoriesDao.getUserById(id)
        }
    }

    private suspend fun getRepositoryById(id: Long): LiveData<DBRepository?> {
        return withContext(Dispatchers.IO) {
            return@withContext database.githubRepositoriesDao.getRepositoryById(id)
        }
    }

    suspend fun getRepositoryByIdAsync(id: Long, scope: CoroutineScope): Deferred<LiveData<Repository?>> {
        return scope.async {
            val liveDataRepo = getRepositoryById(id)
            val repo = Transformations.map(liveDataRepo) {
                return@map it?.asDomainModel()
            }
            repo

        }
    }

    suspend fun getOwnerByIdAsync(id: Long, scope: CoroutineScope): Deferred<LiveData<Owner?>> {
        return scope.async {
            val liveDataOwner = getOwnerById(id)
            val owner = Transformations.map(liveDataOwner) {
                return@map it?.asDomainModel()
            }
            owner
        }
    }

    suspend fun deleteOwners() {
        withContext(Dispatchers.IO) {
            database.githubRepositoriesDao.deleteOwners()
            getDataSourceFactory().create().invalidate()
        }
    }

    private fun getDataSourceFactory(): DataSource.Factory<Int, Repository> {

        val factory = database.githubRepositoriesDao.getRepositories(criteria)
        return factory.map {
            it.asDomainModel()
        }
    }

    fun search(query: String, scope: CoroutineScope): GithubSearchResult {

        val boundaryCallback = GithubSearchBoundaryCallback(query, database, apiService, scope)

        val dataSourceFactory = getDataSourceFactory()

        val data = LivePagedListBuilder(dataSourceFactory, DATABASE_PAGE_SIZE)
            .setBoundaryCallback(boundaryCallback)
            .build()

        Log.i("GithubSearchRepos", "in search repository " + boundaryCallback.networkStatus.value.toString())
        return GithubSearchResult(boundaryCallback.networkStatus, data)
    }


    companion object {
        private const val DATABASE_PAGE_SIZE = 15
    }
}