package com.devjamesp.bookreviewactivity

import android.annotation.SuppressLint
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.devjamesp.bookreviewactivity.adapter.BookAdapter
import com.devjamesp.bookreviewactivity.adapter.HistoryAdapter
import com.devjamesp.bookreviewactivity.api.BookService
import com.devjamesp.bookreviewactivity.databinding.ActivityMainBinding
import com.devjamesp.bookreviewactivity.model.BestSellerDTO
import com.devjamesp.bookreviewactivity.model.History
import com.devjamesp.bookreviewactivity.model.SearchBookDTO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var bookAdapter: BookAdapter
    private lateinit var historyAdapter: HistoryAdapter

    private lateinit var bookService: BookService

    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initBookRecyclerView()
        initHistoryRecyclerView()
        initBookService()
        initDB()
    }

    private fun getBestSellerBooks() {
        bookService.getBestSellerBooks(getString(R.string.INTERPARK_APIKEY))
            .enqueue(object : Callback<BestSellerDTO> {
                override fun onResponse(
                    call: Call<BestSellerDTO>,
                    response: Response<BestSellerDTO>
                ) {
                    if (response.isSuccessful.not()) {
                        Log.d(TAG, "response not success!!")
                        return
                    }

                    response.body()?.let {
                        it.books.forEach { book ->
                        }
                        bookAdapter.submitList(it.books) // submitList??? adapter?????? currentList?????????.
                    }
                }

                override fun onFailure(call: Call<BestSellerDTO>, t: Throwable) {
                    Log.e(TAG, t.message.toString())
                }
            })
    }

    private fun initBookService() {
        val retrofit = Retrofit.Builder()
            .baseUrl("http://book.interpark.com")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        bookService = retrofit.create(BookService::class.java)

        // ?????? ??????????????? ?????? ??????
        getBestSellerBooks()

        // ????????? ??????(??????) ??? ??? ????????? ??????
        initSearchEditText()
    }

    private fun search(keyWord: String) {
        bookService.getBookByName(getString(R.string.INTERPARK_APIKEY), keyWord)
            .enqueue(object : Callback<SearchBookDTO> {
                override fun onResponse(
                    call: Call<SearchBookDTO>,
                    response: Response<SearchBookDTO>
                ) {
                    hideHistoryView()
                    savedSearchKeword(keyWord)

                    if (response.isSuccessful.not()) {
                        throw Exception("response not Success!!!")
                    }

                    /** ????????? ?????????????????? ???????????? ???????????? ????????? books(?????????)??? ????????? ???????????????.
                     * ????????????????????? ???????????? ???????????? ????????????????????? ????????????.
                     */
                    bookAdapter.submitList(response.body()?.books.orEmpty())
                    response.body()?.books.orEmpty().forEach {
                        Log.d(TAG, it.toString())
                    }
                }

                override fun onFailure(call: Call<SearchBookDTO>, t: Throwable) {
                    Log.e(TAG, t.message.toString())
                    hideHistoryView()
                }
            })
    }

    private fun savedSearchKeword(keyword: String) {
        Log.d(ttt, "???????????? db??? ??????")
        CoroutineScope(Dispatchers.Default).launch {
            db.historyDao().insertHistory(History(null, keyword))
            Log.d(ttt, "????????? dbSize : ${db.historyDao().getAll().size}")
        }
    }

    private fun deleteSearchKeyword(keyWord: String) {
        Log.d(ttt, "?????? ???????????? db?????? ?????? ??? ?????????????????? ?????????")
        CoroutineScope(Dispatchers.IO).launch {
            db.historyDao().delete(keyWord)
            showHistoryView()
        }
    }

    private fun showHistoryView() {
        CoroutineScope(Dispatchers.Default).launch {
            Log.d(ttt, "?????? db??? ???????????? ??????(reverse??? :: ????????? ???????????? ????????? ????????? ?????????")
            val keywords = db.historyDao().getAll().reversed()

            CoroutineScope(Dispatchers.Main).launch {
                Log.d(ttt, "???????????? ?????? visible??? ?????????, ???????????? ?????????????????? ??????")
                binding.historyRecyclerView.isVisible = true
                historyAdapter.submitList(keywords.orEmpty())
            }
        }
        binding.historyRecyclerView.isVisible = true
    }

    private fun hideHistoryView() {
        Log.d(ttt, "?????? ?????? ???, ???????????? ?????????????????? ??????")
        binding.historyRecyclerView.isVisible = false
    }

    private fun initBookRecyclerView() {
        bookAdapter = BookAdapter(itemClickedListener = { book ->
            Intent(this@MainActivity, DetailActivity::class.java).also { intent ->
                intent.putExtra("BookInfo", book)
                startActivity(intent)
            }
        })

        binding.bookRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.bookRecyclerView.adapter = bookAdapter
    }

    private fun initHistoryRecyclerView() {
        historyAdapter = HistoryAdapter(
            searchHistoryClickedListener = { keyword ->
                search(keyword)
            },
            historyDeleteClickedListener = { keyword ->
            deleteSearchKeyword(keyword)
        })
        binding.historyRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.historyRecyclerView.adapter = historyAdapter
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initSearchEditText() {
        binding.searchEditText.setOnKeyListener { v, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == MotionEvent.ACTION_DOWN) {
                search(binding.searchEditText.text.toString())

                // ?????? ?????? ??? editText??? ??????????????? ?????? ?????? ???????????? ??????
                val i = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                i.hideSoftInputFromWindow(binding.searchEditText.windowToken, 0)
                return@setOnKeyListener true
            }
            return@setOnKeyListener false
        }

        binding.searchEditText.setOnTouchListener(View.OnTouchListener { v, event ->
            if(event.action == MotionEvent.ACTION_DOWN) {
                showHistoryView()
                // true??? ?????? action_down??? ????????? ???????????? ????????? ?????? ????????? ???????????? ???????????? true??? return?????? ??????
            }
            return@OnTouchListener false
        })
    }

    private fun initDB() {
        db = getAppDatabase(this)
    }


    companion object {
        private const val TAG = "MainActivity"
        private const val ttt = "Coroutine"
    }

}