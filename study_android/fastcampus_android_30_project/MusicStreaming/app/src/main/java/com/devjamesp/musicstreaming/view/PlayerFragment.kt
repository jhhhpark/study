package com.devjamesp.musicstreaming.view

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.devjamesp.musicstreaming.R
import com.devjamesp.musicstreaming.databinding.FragmentPlayerBinding
import com.devjamesp.musicstreaming.service.MusicService
import com.devjamesp.musicstreaming.service.model.*
import com.devjamesp.musicstreaming.view.adapter.PlayListAdapter
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.sql.Time
import java.util.concurrent.TimeUnit
import java.util.logging.SimpleFormatter
import kotlin.concurrent.timer

class PlayerFragment : Fragment(R.layout.fragment_player) {

    private var binding: FragmentPlayerBinding? = null
    private lateinit var model : PlayerModel

    private lateinit var playListAdapter : PlayListAdapter
    private var player: SimpleExoPlayer? = null

    private val updateSeekRunnable = Runnable { updateSeek() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val fragmentPlayerBinding = FragmentPlayerBinding.bind(view)
        binding = fragmentPlayerBinding

        initPlayerView(fragmentPlayerBinding)
        initPlayListButton(fragmentPlayerBinding)
        initPlayControlButtons(fragmentPlayerBinding)
        initSeekBar(fragmentPlayerBinding)
        initPlayListRecyclerView(fragmentPlayerBinding)

        getVideoListFromServer()
    }


    private fun initPlayerView(fragmentPlayerBinding: FragmentPlayerBinding) {
        context?.let {
            player = SimpleExoPlayer.Builder(it).build()
        }

        fragmentPlayerBinding.playerView.player = player

        binding?.let { binding ->
            player?.addListener(object: Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    super.onIsPlayingChanged(isPlaying)
                    if (isPlaying) {
                        binding.playControllerImageView.setImageResource(R.drawable.ic_baseline_pause_24)
                    } else {
                        binding.playControllerImageView.setImageResource(R.drawable.ic_baseline_play_arrow_24)
                    }
                }


                override fun onPlaybackStateChanged(state: Int) {
                    super.onPlaybackStateChanged(state)
                    updateSeek()
                }


                // ????????? Item??? ??????????????? ????????? ???????????? ????????????
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    super.onMediaItemTransition(mediaItem, reason)
                    val newIndex = mediaItem?.mediaId ?: return

                    // model??? ???????????? MediaItem??? ??????????????? ????????????
                    model.currentPosition = newIndex.toInt()

                    // ???????????? Media??? ???????????? ?????? ????????????
                    updatePlayerView(model.currentMusicModel())

                    // ???????????? Media??? ????????? ???????????? ?????????????????? ??????
                    playListAdapter.submitList(model.getAdapterModels())
                }
            })
        }
    }

    private fun updateSeek() {
        val player = this.player ?: return

        val duration = if (player.duration >= 0) player.duration else 0
        val position = player.currentPosition

        // todo ui update
        updateSeekUi(duration, position)

        val state = player.playbackState

        view?.removeCallbacks(updateSeekRunnable)
        // ?????? ??? ?????? ????????? ?????? ????????? ????????????
        if (state != Player.STATE_IDLE && state != Player.STATE_ENDED) {
            view?.postDelayed(updateSeekRunnable, 1000L)
        }

    }

    private fun updateSeekUi(duration: Long, position: Long) {
        binding?.let { binding ->
            binding.playListSeekBar.max = (duration / 1000).toInt()
            binding.playListSeekBar.progress = (position / 1000).toInt()
            binding.playerSeekBar.max = (duration / 1000).toInt()
            binding.playerSeekBar.progress = (position / 1000).toInt()

            binding.playTimeTextView.text = String.format("%02d:%02d",
                (position / 1000) / 60, //TimeUnit.MINUTES.convert(position, TimeUnit.MILLISECONDS)
                (position / 1000) % 60)
            binding.totalTimeTextView.text = String.format("%02d:%02d",
                (duration / 1000) / 60, //TimeUnit.MINUTES.convert(position, TimeUnit.MILLISECONDS)
                (duration / 1000) % 60)
        }
    }

    private fun updatePlayerView(currentMusicModel: MusicModel?) {
        currentMusicModel ?: return

        binding?.let { binding ->
            binding.trackTextView.text = currentMusicModel.track
            binding.artistTextView.text = currentMusicModel.artist

            Glide.with(binding.coverImageView.context)
                .load(currentMusicModel.coverUrl)
                .into(binding.coverImageView)
        }
    }

    // ????????? ???????????? ???????????? ?????? ????????? ?????? ?????? ?????? ?????????
    private fun initPlayListButton(fragmentPlayerBinding: FragmentPlayerBinding) {
        fragmentPlayerBinding.playListImageView.setOnClickListener {
            if (model.currentPosition == -1) return@setOnClickListener

            fragmentPlayerBinding.playListViewGroup.isVisible = model.isWatchingPlayListView
            fragmentPlayerBinding.playerViewGroup.isVisible = model.isWatchingPlayListView.not()

            model.isWatchingPlayListView = model.isWatchingPlayListView.not()
        }
    }


    @SuppressLint("ClickableViewAccessibility")
    private fun initSeekBar(fragmentPlayerBinding: FragmentPlayerBinding) {
        fragmentPlayerBinding.playerSeekBar.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) { }
            override fun onStartTrackingTouch(seekBar: SeekBar) { }
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                player?.seekTo((seekBar.progress * 1000).toLong())
            }
        })

        fragmentPlayerBinding.playListSeekBar.setOnTouchListener { _, _ -> false }
    }
    
    // ?????? ????????? ????????? ?????? setup
    private fun initPlayControlButtons(fragmentPlayerBinding: FragmentPlayerBinding) {
        fragmentPlayerBinding.playControllerImageView.setOnClickListener {
            val player = this.player ?: return@setOnClickListener
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        }

        fragmentPlayerBinding.skipNextImageView.setOnClickListener {
            val nextMusic = model.getNextMusic() ?: return@setOnClickListener
            playMusic(nextMusic)
        }

        fragmentPlayerBinding.skipPreviewImageView.setOnClickListener {
            val prevMusic = model.getPreviousMusic() ?: return@setOnClickListener
            playMusic(prevMusic)
        }
    }


    // ?????????????????? ??????
    private fun initPlayListRecyclerView(fragmentPlayerBinding: FragmentPlayerBinding) {
        playListAdapter = PlayListAdapter {
            // ????????? ?????? ??? ???????????? ??????
            playMusic(it)
        }

        fragmentPlayerBinding.playListRecyclerView.apply {
            adapter = playListAdapter
            layoutManager = LinearLayoutManager(context)
        }
    }

    // ??????????????? ?????? ???????????? ?????????
    private fun getVideoListFromServer() {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://run.mocky.io/v3/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        retrofit.create(MusicService::class.java).also {
            it.listMusics()
                .enqueue(object : Callback<MusicDTO> {
                    override fun onResponse(call: Call<MusicDTO>, response: Response<MusicDTO>) {
                        if (response.isSuccessful.not()) {
                            Toast.makeText(context, "?????? ????????? ???????????? ???????????? ???????????????.", Toast.LENGTH_SHORT).show()
                        } else {
                            response.body()?.let { musicDTO ->
                                model = musicDTO.mapToPlayerModel()

                                // player??? ?????????????????? Item?????? ????????? set?????? ??????
                                setMusiclList(model.getAdapterModels())

                                // RecyclerView??? ?????????????????? Update
                                playListAdapter.submitList(model.getAdapterModels())


                            }
                        }
                    }

                    override fun onFailure(call: Call<MusicDTO>, t: Throwable) {
                        Toast.makeText(context, "?????? ????????? ???????????? ???????????? ???????????????.", Toast.LENGTH_SHORT).show()
                    }
                })
        }
    }

    // ??????????????? ????????? ?????? ???????????? setup
    private fun setMusiclList(modelList: List<MusicModel>) {
        context?.let {
            // ??????????????? ?????????????????? set, currentPosition??? 0?????? ?????????
            player?.addMediaItems(modelList.map { musicModel ->
                MediaItem.Builder()
                    .setMediaId(musicModel.id.toString())
                    .setUri(musicModel.streamUrl)
                    .build()
            })
            player?.prepare()
        }
    }

    // ?????? ???????????? ????????? ??????????????? ????????? ?????? ??????
    private fun playMusic(musicModel: MusicModel) {
        model.updateCurrentPosition(musicModel)

        player?.seekTo(model.currentPosition, 0)
        player?.play()
    }


    override fun onStop() {
        super.onStop()

        player?.pause()
        view?.removeCallbacks(updateSeekRunnable)
    }


    override fun onDestroy() {
        super.onDestroy()

        binding = null
        player?.release()
        view?.removeCallbacks(updateSeekRunnable)
    }


    companion object {
        fun newInstance(): PlayerFragment {
            return PlayerFragment()
        }
    }
}