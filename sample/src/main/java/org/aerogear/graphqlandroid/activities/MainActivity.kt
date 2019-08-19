package org.aerogear.graphqlandroid.activities

import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.text.Html
import android.util.Log
import android.view.LayoutInflater
import android.widget.Toast
import androidx.work.Constraints
import androidx.work.NetworkType
import com.apollographql.apollo.ApolloCall
import com.apollographql.apollo.ApolloQueryWatcher
import com.apollographql.apollo.api.Mutation
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.apollo.fetcher.ApolloResponseFetchers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.alertfrag_create_tasks.view.*
import kotlinx.android.synthetic.main.alertfrag_create_user.view.*
import org.aerogear.graphqlandroid.*
import org.aerogear.graphqlandroid.adapter.TaskAdapter
import org.aerogear.graphqlandroid.model.NamePair
import org.aerogear.graphqlandroid.model.UserOutput
import org.aerogear.graphqlandroid.type.TaskInput
import org.aerogear.graphqlandroid.type.UserFilter
import org.aerogear.graphqlandroid.type.UserInput
import org.aerogear.graphqlandroid.worker.SampleWorker
import org.aerogear.offix.enqueue
import org.aerogear.offix.interfaces.ResponseCallback
import org.aerogear.offix.scheduleWorker
import java.util.concurrent.atomic.AtomicReference

class MainActivity : AppCompatActivity() {

    var tasksList = arrayListOf<UserOutput>()
    val TAG = javaClass.simpleName
    val taskAdapter by lazy {
        TaskAdapter(tasksList, this)
    }
    val constraints by lazy {
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
    }
    private val disposables = CompositeDisposable()
    val watchResponse = AtomicReference<Response<FindAllTasksQuery.Data>>()
    var apolloQueryWatcher: ApolloQueryWatcher<FindAllTasksQuery.Data>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fabAdd.setOnClickListener {
            val options = arrayOf("Create a Task", "Assign a Task")
            val builder = AlertDialog.Builder(this).setTitle("Choose an option!")
            builder.setItems(options) { dialog, which ->
                when (which) {
                    0 -> {
                        //Used for creating a new task
                        val inflatedView =
                            LayoutInflater.from(this).inflate(R.layout.alertfrag_create_tasks, null, false)
                        val customAlert: AlertDialog = AlertDialog.Builder(this)
                            .setView(inflatedView)
                            .setTitle("Create a new Note")
                            .setNegativeButton("No") { dialog, which ->
                                dialog.dismiss()
                            }
                            .setPositiveButton("Yes") { dialog, which ->
                                val title = inflatedView.etTitleTask.text.toString()
                                val desc = inflatedView.etDescTask.text.toString()
                                val version = inflatedView.etVerTask.text.toString()
                                createtask(title, desc, version.toInt())
                                dialog.dismiss()
                            }
                            .create()
                        customAlert.show()
                    }
                    1 -> {
                        //Used for assigning user
                        val inflatedView =
                            LayoutInflater.from(this).inflate(R.layout.alertfrag_create_user, null, false)
                        val customAlert: AlertDialog = AlertDialog.Builder(this)
                            .setView(inflatedView)
                            .setTitle("Assign the User a task")
                            .setNegativeButton("No") { dialog, which ->
                                dialog.dismiss()
                            }
                            .setPositiveButton("Yes") { dialog, which ->
                                val taskId = inflatedView.etTaskIdUser.text.toString()
                                val title = inflatedView.etTitleUser.text.toString()
                                val firstName = inflatedView.etFirstName.text.toString()
                                val lastName = inflatedView.etLastName.text.toString()
                                val email = inflatedView.etEmail.text.toString()
                                createUser(title, firstName, lastName, email, taskId)
                                dialog.dismiss()
                            }
                            .create()
                        customAlert.show()
                    }
                }
            }

            // create and show the alert dialog
            val dialog = builder.create()
            dialog.show()
        }
        recycler_view.layoutManager = LinearLayoutManager(this)
        recycler_view.adapter = taskAdapter

        getTasks()

        pull_to_refresh.setOnRefreshListener {
            doYourUpdate()
            pull_to_refresh.isRefreshing = false
        }
    }

    private fun doYourUpdate() {

        Log.e(TAG, " -*-*-*- doYourUpdate")
        tasksList.clear()

        Utils.getApolloClient(this)?.query(
            FindAllTasksQuery.builder().build()
        )?.watcher()
            ?.refetchResponseFetcher(ApolloResponseFetchers.CACHE_FIRST)
            ?.enqueueAndWatch(object : ApolloCall.Callback<FindAllTasksQuery.Data>() {
                override fun onFailure(e: ApolloException) {
                    e.printStackTrace()
                    Log.e(TAG, " doYourUpdate onFailure----$e ")
                }

                override fun onResponse(response: Response<FindAllTasksQuery.Data>) {

                    watchResponse.set(response)

                    Log.e(TAG, "on Response doYourUpdate: Watcher ${response.data()}")

                    val result = watchResponse.get()?.data()?.findAllTasks()
                    result?.forEach {
                        val title = it.title()
                        val desc = it.description()
                        val id = it.id()
                        var firstName = ""
                        var lastName = ""
                        it.assignedTo()?.let {
                            firstName = it.firstName()
                            lastName = it.lastName()
                        } ?: kotlin.run {
                            firstName = "User Not assigned"
                            lastName = ""
                        }
                        val taskOutput = UserOutput(title, desc, id.toInt(), firstName, lastName)
                        runOnUiThread {
                            tasksList.add(taskOutput)
                            taskAdapter.notifyDataSetChanged()
                        }
                    }
                }
            })

        pull_to_refresh.isRefreshing = false
    }

    fun getTasks() {
        Log.e(TAG, " ----- getTasks")

        tasksList.clear()

        FindAllTasksQuery.builder()?.build()?.let {
            Utils.getApolloClient(this)?.query(it)
                ?.responseFetcher(ApolloResponseFetchers.CACHE_FIRST)
                ?.enqueue(object : ApolloCall.Callback<FindAllTasksQuery.Data>() {

                    override fun onFailure(e: ApolloException) {
                        e.printStackTrace()
                        Log.e(TAG, "getTasks ----$e ")
                    }

                    override fun onResponse(response: Response<FindAllTasksQuery.Data>) {
                        Log.e(TAG, "on Response getTasks : Data ${response.data()}")
                        val result = response.data()?.findAllTasks()

                        result?.forEach {
                            val title = it.title()
                            val desc = it.description()
                            val id = it.id()
                            var firstName = ""
                            var lastName = ""
                            it.assignedTo()?.let {
                                firstName = it.firstName()
                                lastName = it.lastName()
                            } ?: kotlin.run {
                                firstName = "User Not assigned"
                                lastName = ""
                            }
                            val taskOutput = UserOutput(title, desc, id.toInt(), firstName, lastName)
                            runOnUiThread {
                                tasksList.add(taskOutput)
                                taskAdapter.notifyDataSetChanged()
                            }
                        }
                    }
                })
        }
    }

    fun getUser(id: Int): NamePair {
        Log.e("${TAG} Inside getUser", "TaskId: $id")
        var namePair = NamePair("", "")

        val userFilter = UserFilter.builder().id(id.toString()).build()
        FindUsersQuery.builder().fields(userFilter).build()?.let {
            Utils.getApolloClient(this)?.query(it)?.responseFetcher(ApolloResponseFetchers.CACHE_FIRST)
                ?.enqueue(object : ApolloCall.Callback<FindUsersQuery.Data>() {
                    override fun onFailure(e: ApolloException) {
                        e.printStackTrace()
                        Log.e(TAG, "getUser----$e ")
                    }

                    override fun onResponse(response: Response<FindUsersQuery.Data>) {
                        Log.e(TAG, "on Response : response.data ${response.data()}")
                        val result = response.data()?.findUsers()

                        result?.forEach {
                            var firstName = it.firstName()
                            var lastName = it.lastName()
                            Log.e("${TAG}100", "$title")
                            Log.e("${TAG}138", firstName)
                            namePair.fName = firstName
                            namePair.lName = lastName
                        }
                    }
                })
        }
        return namePair
    }

    fun updateTask(id: String, title: String, version: Int, description: String) {
        Log.e(TAG, "inside update title in MainActivity")

        /*
        As version is assumed to be auto incremented ( //TODO Have to make changes in sqlite db)
        */
        val input = TaskInput.builder().title(title).version(version).description(description).status("test").build()

        var mutation = UpdateTaskMutation.builder().id(id).input(input).build()

        Log.e(TAG, " updateTask ********: - $mutation")

        val client = Utils.getApolloClient(this)?.mutate(
            mutation
        )?.refetchQueries(apolloQueryWatcher?.operation()?.name())

        Log.e(TAG, " updateTask class name: - ${mutation.javaClass.simpleName}")
        Log.e(
            TAG,
            " updateTask 20: - ${client?.requestHeaders(com.apollographql.apollo.request.RequestHeaders.builder().build())}"
        )
        Log.e(TAG, " updateTask 21: - ${client?.operation()?.queryDocument()}")
        Log.e(TAG, " updateTask 22: - ${client?.operation()?.variables()?.valueMap()}")
        Log.e(TAG, " updateTask 23: - ${client?.operation()?.name()}")

        val customCallback = object : ResponseCallback {

            override fun onSuccess(response: Response<Any>) {
                Log.e("onSuccess() updateTask", "${response.data()}")
                val result = response.data()

                //In case of conflicts data returned from the server id null.
                result?.let {
                    Log.e(TAG, "onResponse-UpdateTask- $it")
                }
            }

            override fun onSchedule(e: ApolloException, mutation: Mutation<Operation.Data, Any, Operation.Variables>) {
                Log.e("onSchedule() updateTask", "${mutation.variables().valueMap()}")
                e.printStackTrace()
            }
        }

        Utils.getApolloClient(this)?.enqueue(
            mutation as com.apollographql.apollo.api.Mutation<Operation.Data, Any, Operation.Variables>,
            customCallback
        )
    }

    fun createtask(title: String, description: String, version: Int) {
        Log.e(TAG, "inside create title")

        /*
         As version is assumed to be auto incremented ( //TODO Have to make changes in sqlite db)
         */
        val input = TaskInput.builder().title(title).description(description).version(version).status("test").build()

        val mutation = CreateTaskMutation.builder().input(input).build()

        val client = Utils.getApolloClient(this)?.mutate(
            mutation
        )?.refetchQueries(apolloQueryWatcher?.operation()?.name())

        Log.e(TAG, " updateTask 22: - ${client?.operation()?.variables()?.valueMap()}")

        val customCallback = object : ResponseCallback {

            override fun onSuccess(response: Response<Any>) {
                Log.e("onSuccess() createTask", "${response.data()}")
            }

            override fun onSchedule(e: ApolloException, mutation: Mutation<Operation.Data, Any, Operation.Variables>) {
                e.printStackTrace()
                Log.e("onSchedule() createTask", "${mutation.variables().valueMap()}")
            }
        }

        Utils.getApolloClient(this)?.enqueue(
            mutation as com.apollographql.apollo.api.Mutation<Operation.Data, Any, Operation.Variables>,
            customCallback
        )

        Toast.makeText(this, "Mutation with title $title is created", Toast.LENGTH_LONG).show()
    }

    fun createUser(title: String, firstName: String, lastName: String, email: String, taskId: String) {
        Log.e(TAG, "inside create user")

        /*
         As version is assumed to be auto incremented ( //TODO Have to make changes in sqlite db)
         */
        val input = UserInput.builder().taskId(taskId).email(email).firstName(firstName).lastName(lastName).title(title)
            .creationmetadataId(taskId).build()

        val mutation = CreateUserMutation.builder().input(input).build()

        val client = Utils.getApolloClient(this)?.mutate(
            mutation
        )?.refetchQueries(apolloQueryWatcher?.operation()?.name())

        Log.e(TAG, " createUser 22: - ${client?.operation()?.variables()?.valueMap()}")

        val customCallback = object : ResponseCallback {

            override fun onSuccess(response: Response<Any>) {
                Log.e("onSuccess() createTask", "${response.data()}")
            }

            override fun onSchedule(e: ApolloException, mutation: Mutation<Operation.Data, Any, Operation.Variables>) {
                e.printStackTrace()
                Log.e("onSchedule() createUser", "${mutation.variables().valueMap()}")
            }
        }

        Utils.getApolloClient(this)?.enqueue(
            mutation as com.apollographql.apollo.api.Mutation<Operation.Data, Any, Operation.Variables>,
            customCallback
        )

        Toast.makeText(this, "Task with id $taskId is assigned to $firstName $lastName", Toast.LENGTH_LONG).show()
    }

    fun getAllUsers() {
        Log.e(TAG, " ----- getAllUsers")

        FindAllUsersQuery.builder()?.build()?.let {
            Utils.getApolloClient(this)?.query(it)
                ?.responseFetcher(ApolloResponseFetchers.CACHE_FIRST)
                ?.enqueue(object : ApolloCall.Callback<FindAllUsersQuery.Data>() {

                    override fun onFailure(e: ApolloException) {
                        e.printStackTrace()
                        Log.e(TAG, "----$e ")
                    }

                    override fun onResponse(response: Response<FindAllUsersQuery.Data>) {
                        Log.e(TAG, "on Response : response.data ${response.data()}")
                        val result = response.data()?.findAllUsers()


                        result?.forEach {
                            val title = it.title()
                            val email = it.email()
                            val taskId = it.taskId()
                            val firstName = it.firstName()
                            val lastName = it.lastName()
                            Log.e("${TAG}10", "$title")
                            Log.e("${TAG}11", "$email")
                            Log.e("${TAG}12", "$taskId")
                            Log.e("${TAG}13", "$firstName")
                            // UI
                        }
                        runOnUiThread {
                            //
                        }
                    }
                })
        }
    }

    /*
     In the activity's onStop(), schedule a worker that would try to replicate the mutations done when offline (stored in database )
     to the server whenever network connection is regained.
     */
    override fun onStop() {
        /* Schedule a worker by calling scheduleWorker() of the library.
           @param Worker class which extend OffixWorker class of the library.
        */
        scheduleWorker(SampleWorker::class.java)
        super.onStop()
    }

    override fun onDestroy() {
        apolloQueryWatcher?.cancel()
        disposables.dispose()
        super.onDestroy()
    }
}

