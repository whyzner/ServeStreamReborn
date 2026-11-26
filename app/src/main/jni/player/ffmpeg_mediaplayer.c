/*
 * FFmpegMediaPlayer: A unified interface for playing audio files and streams.
 *
 * Copyright 2014 William Seemann
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavutil/opt.h>
#include <libswresample/swresample.h>
#include <libswscale/swscale.h>
#include <ffmpeg_mediaplayer.h>
#include <android/log.h>

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
// unistd.h declares pause(void), which clashes with the player's pause(State **)
#define pause unistd_pause_unused
#include <unistd.h>
#undef pause

const char *DURATION = "duration";
const char *AUDIO_CODEC = "audio_codec";
const char *VIDEO_CODEC = "video_codec";
const char *ARTIST = "artist";
const char *TITLE = "title";

const int SUCCESS = 0;
const int FAILURE = -1;

static pthread_mutex_t lock_storage = PTHREAD_MUTEX_INITIALIZER;
static pthread_mutex_t *lock = &lock_storage;

static void close_audio(State *state)
{
	if (state->audio_codec_ctx) {
		avcodec_free_context(&state->audio_codec_ctx);
	}
	if (state->swr_ctx) {
		swr_free(&state->swr_ctx);
	}
	state->swr_in_fmt = -1;
	state->swr_in_rate = 0;
	state->swr_in_channels = 0;
}

int decode_interrupt_cb(void *opaque) {
	State *state = (State *) opaque;
	 
    return (state && state->abort_request);
}

void init(State **ps)
{
    State *state = *ps;

    if (!state) {
        state = av_mallocz(sizeof(State));
        if (state) {
            state->fd = -1;
            state->audio_stream = -1;
            state->video_stream = -1;
            state->last_paused = -1;
            state->swr_in_fmt = -1;
        }
    }

    *ps = state;
}

void clear_l(State **ps)
{
	State *state = *ps;
	        
	if (state) {
		if (state->pFormatCtx) {
			avformat_close_input(&state->pFormatCtx);
	    }
		close_audio(state);
	}
	
	if (state && state->fd != -1) {
		close(state->fd);
	}
	
	state->pFormatCtx = NULL;
	state->audio_stream = -1;
	state->video_stream = -1;
	state->audio_st = NULL;
	state->video_st = NULL;
	state->buffer_size = -1;
	state->abort_request = 0;
	state->paused = 0;
	state->last_paused = -1;
	state->filename[0] = '\0';
	state->headers[0] = '\0';
	state->fd = -1;
	state->offset = 0;
	state->decoder_started = 0;
}

/* Stops the prepare and decoder threads (if running) before the state is touched. */
static void stop_threads(State *state)
{
	if (!state) {
		return;
	}
	state->abort_request = 1;
	if (state->prepare_started) {
		pthread_join(state->prepare_thread, NULL);
		state->prepare_started = 0;
	}
	if (state->decoder_started) {
		pthread_join(state->decoder_thread, NULL);
		state->decoder_started = 0;
	}
}

void disconnect(State **ps)
{
	State *state = *ps;
	        
	if (state) {
		stop_threads(state);
		close_audio(state);
		if (state->pFormatCtx) {
			avformat_close_input(&state->pFormatCtx);
	    }
	           
		if (state && state->fd != -1) {
			close(state->fd);
		}
		
	    av_freep(&state);
	    *ps = NULL;
	}
}

int setNotifyListener(State **ps, void* clazz, void (*listener) (void*, int, int, int, int))
{
    State *state = *ps;
    state->clazz = clazz;
    state->notify_callback = listener;
    return SUCCESS;
}

int setInitAudioTrackListener(State **ps, void* clazz, int (*listener) (void*, int, int, int))
{
    State *state = *ps;
    state->clazz = clazz;
    state->init_audio_track_callback = listener;
    return SUCCESS;
}

int setWriteAudioListener(State **ps, void* clazz, void (*listener) (void*, int16_t *, int, int))
{
    State *state = *ps;
    state->clazz = clazz;
    state->write_audio_callback = listener;
    return SUCCESS;
}

int setDataSourceURI(State **ps, const char *url, const char *headers)
{
    printf("setDataSource\n");

    if (!url) {
    	return FAILURE;
    }

    State *state = *ps;

    // Workaround for FFmpeg ticket #998
    // "must convert mms://... streams to mmsh://... for FFmpeg to work"
    if (strncmp(url, "mms://", 6) == 0) {
    	snprintf(state->filename, sizeof(state->filename), "mmsh://%s", url + 6);
    } else {
    	strncpy(state->filename, url, sizeof(state->filename) - 1);
    	state->filename[sizeof(state->filename) - 1] = '\0';
    }

    if (headers) {
        strncpy(state->headers, headers, sizeof(state->headers) - 1);
        state->headers[sizeof(state->headers) - 1] = '\0';
    }
    
    return SUCCESS;
}

int setDataSourceFD(State **ps, int fd, int64_t offset, int64_t length) {
	printf("setDataSource\n");
	
	State *state = *ps;
	
    int myfd = dup(fd);

    char str[20];
    sprintf(str, "pipe:%d", myfd);
    strncpy(state->filename, str, sizeof(state->filename));
    
    state->fd = myfd;
    state->offset = offset;
    
	*ps = state;

	return SUCCESS;
}

int setMetadataFilter(State **ps, char *allow[], char *block[])
{
    State *state = *ps;
	
    __android_log_print(ANDROID_LOG_INFO, "TAG", "Allow %s \n", allow[0]);
    __android_log_print(ANDROID_LOG_INFO, "TAG", "Block %s \n", block[0]);
    
    //state->allow = allow;
    //state->block = block;
    
	return SUCCESS;
}

void get_duration(AVFormatContext *ic, char * value)
{
	int duration = 0;

	if (ic) {
		if (ic->duration != AV_NOPTS_VALUE) {
			duration = ((ic->duration / AV_TIME_BASE) * 1000);
		}
	}

	sprintf(value, "%d", duration); // %i
}

void set_codec(AVFormatContext *ic, int i)
{
    const char *codec_type = av_get_media_type_string(ic->streams[i]->codecpar->codec_type);

	if (!codec_type) {
		return;
	}

    const char *codec_name = avcodec_get_name(ic->streams[i]->codecpar->codec_id);

	if (strcmp(codec_type, "audio") == 0) {
		av_dict_set(&ic->metadata, AUDIO_CODEC, codec_name, 0);
    } else if (codec_type && strcmp(codec_type, "video") == 0) {
	   	av_dict_set(&ic->metadata, VIDEO_CODEC, codec_name, 0);
	}
}

int get_metadata(State **ps, const char* key, char** value)
{
    *value = NULL;
	
	State *state = *ps;
    
	if (!state || !state->pFormatCtx) {
		return FAILURE;
	}

	if (key) {
		if (av_dict_get(state->pFormatCtx->metadata, key, NULL, AV_DICT_IGNORE_SUFFIX)) {
			*value = av_dict_get(state->pFormatCtx->metadata, key, NULL, AV_DICT_IGNORE_SUFFIX)->value;
		}
	}

	return SUCCESS;
}

/*int setVideoSurface(const sp<Surface>& surface)
{
    return 0;
}*/

static int ensure_swr(State *state, AVFrame *frame)
{
	int in_ch = frame->ch_layout.nb_channels;

	if (state->swr_ctx && state->swr_in_fmt == frame->format &&
			state->swr_in_rate == frame->sample_rate && state->swr_in_channels == in_ch) {
		return 0;
	}

	if (state->swr_ctx) {
		swr_free(&state->swr_ctx);
	}

	AVChannelLayout in_layout, out_layout;
	memset(&in_layout, 0, sizeof(in_layout));
	memset(&out_layout, 0, sizeof(out_layout));

	if (frame->ch_layout.order == AV_CHANNEL_ORDER_UNSPEC) {
		av_channel_layout_default(&in_layout, in_ch);
	} else if (av_channel_layout_copy(&in_layout, &frame->ch_layout) < 0) {
		return -1;
	}

	av_channel_layout_default(&out_layout, state->swr_out_channels > 0 ? state->swr_out_channels : (in_ch > 2 ? 2 : in_ch));

	int ret = swr_alloc_set_opts2(&state->swr_ctx, &out_layout, AV_SAMPLE_FMT_S16, frame->sample_rate,
			&in_layout, (enum AVSampleFormat) frame->format, frame->sample_rate, 0, NULL);
	av_channel_layout_uninit(&in_layout);
	av_channel_layout_uninit(&out_layout);

	if (ret < 0 || !state->swr_ctx || swr_init(state->swr_ctx) < 0) {
		__android_log_print(ANDROID_LOG_ERROR, "TAG", "Failed to initialize the resampling context\n");
		swr_free(&state->swr_ctx);
		return -1;
	}

	state->swr_in_fmt = frame->format;
	state->swr_in_rate = frame->sample_rate;
	state->swr_in_channels = in_ch;
	return 0;
}

int decode_frame_from_packet(State *state, AVPacket *pkt, int *frame_size_ptr, int from_thread)
{
	int total = 0;
	int ret;

	*frame_size_ptr = 0;

	if (!pkt || pkt->stream_index != state->audio_stream || !state->audio_codec_ctx) {
		return 0;
	}

	ret = avcodec_send_packet(state->audio_codec_ctx, pkt);
	if (ret < 0 && ret != AVERROR(EAGAIN) && ret != AVERROR_EOF) {
		__android_log_print(ANDROID_LOG_ERROR, "TAG", "avcodec_send_packet() failed: %d", ret);
		return -2;
	}

	AVFrame *frame = av_frame_alloc();
	if (!frame) {
		__android_log_print(ANDROID_LOG_INFO, "TAG", "Could not allocate audio frame\n");
		return -2;
	}

	while (avcodec_receive_frame(state->audio_codec_ctx, frame) >= 0) {
		uint8_t *samples = NULL;
		int data_size = 0;
		int out_ch = state->swr_out_channels > 0 ? state->swr_out_channels : frame->ch_layout.nb_channels;

		if (frame->format == AV_SAMPLE_FMT_S16 && frame->ch_layout.nb_channels == out_ch) {
			data_size = av_samples_get_buffer_size(NULL, out_ch, frame->nb_samples, AV_SAMPLE_FMT_S16, 1);
			if (data_size > 0) {
				samples = av_malloc(data_size);
				if (samples) {
					memcpy(samples, frame->data[0], data_size);
				}
			}
		} else if (ensure_swr(state, frame) == 0) {
			int max_out = swr_get_out_samples(state->swr_ctx, frame->nb_samples) + 256;
			samples = av_malloc((size_t) max_out * out_ch * 2);
			if (samples) {
				int converted = swr_convert(state->swr_ctx, &samples, max_out,
						(const uint8_t **) frame->extended_data, frame->nb_samples);
				if (converted > 0) {
					data_size = converted * out_ch * 2;
				} else {
					av_freep(&samples);
				}
			}
		}

		if (samples && data_size > 0) {
			total += data_size;

			state->audio_clock += (double) data_size / (double) (2 * out_ch * frame->sample_rate);
			if (pkt->pts != AV_NOPTS_VALUE) {
				state->audio_clock = av_q2d(state->audio_st->time_base) * pkt->pts;
			}

			state->write_audio_callback(state->clazz, (int16_t *) samples, data_size, from_thread);
		}

		av_freep(&samples);
		av_frame_unref(frame);
	}

	av_frame_free(&frame);

	*frame_size_ptr = total;
	return total > 0 ? AUDIO_DATA_ID : 0;
}

void *player_decode(void *data)
{
	State *state = (State *) data;
	
	int ret;
	int eof = 0;
	AVPacket *packet = av_packet_alloc();

	if (!packet) {
		return NULL;
	}

	for (;;) {

		if (state->abort_request) {
			break;
		}

        if (state->paused != state->last_paused) {
        	state->last_paused = state->paused;
            if (state->paused) {
            	state->read_pause_return = av_read_pause(state->pFormatCtx);
            } else {
                av_read_play(state->pFormatCtx);
            }
        }

        if (state->seek_req) {
            int64_t seek_target = state->seek_pos;
            int64_t seek_min = state->seek_rel > 0 ? seek_target - state->seek_rel + 2: INT64_MIN;
            int64_t seek_max = state->seek_rel < 0 ? seek_target - state->seek_rel - 2: INT64_MAX;

            ret = avformat_seek_file(state->pFormatCtx, -1, seek_min, seek_target, seek_max, state->seek_flags);
            if (ret < 0) {
                fprintf(stderr, "%s: error while seeking\n", state->pFormatCtx->url);
            } else {
                if (state->audio_codec_ctx) {
                	avcodec_flush_buffers(state->audio_codec_ctx);
                }
                state->notify_callback(state->clazz, MEDIA_SEEK_COMPLETE, 0, 0, FROM_THREAD);
            }
            state->seek_req = 0;
            eof = 0;
        }

        if (state->paused) {
        	usleep(10000);
        	continue;
        }

		ret = av_read_frame(state->pFormatCtx, packet);

		if (ret < 0) {
			if (state->abort_request) {
				break;
			}
			if (ret == AVERROR(EAGAIN)) {
				usleep(1000);
				continue;
			}
			eof = 1;
			break;
		}

		int frame_size = 0;
		decode_frame_from_packet(state, packet, &frame_size, FROM_THREAD);
		av_packet_unref(packet);
	}

	av_packet_free(&packet);

	if (eof && !state->abort_request) {
		state->notify_callback(state->clazz, MEDIA_PLAYBACK_COMPLETE, 0, 0, FROM_THREAD);
	}

	return NULL;
}

int stream_component_open(State *s, int stream_index, int from_thread)
{
	AVFormatContext *pFormatCtx = s->pFormatCtx;
	AVStream *stream;
	const AVCodec *codec;

	if (stream_index < 0 || stream_index >= pFormatCtx->nb_streams) {
		return FAILURE;
	}

	stream = pFormatCtx->streams[stream_index];

	if (stream->codecpar->codec_type == AVMEDIA_TYPE_VIDEO) {
		s->video_stream = stream_index;
		s->video_st = stream;
		return SUCCESS;
	}

	if (stream->codecpar->codec_type != AVMEDIA_TYPE_AUDIO) {
		return SUCCESS;
	}

	codec = avcodec_find_decoder(stream->codecpar->codec_id);

	if (codec == NULL) {
	    printf("avcodec_find_decoder() failed to find audio decoder\n");
	    return FAILURE;
	}

	AVCodecContext *codecCtx = avcodec_alloc_context3(codec);
	if (!codecCtx) {
		return FAILURE;
	}

	if (avcodec_parameters_to_context(codecCtx, stream->codecpar) < 0) {
		avcodec_free_context(&codecCtx);
		return FAILURE;
	}

	codecCtx->pkt_timebase = stream->time_base;

    if (avcodec_open2(codecCtx, codec, NULL) < 0) {
	  	printf("avcodec_open2() failed\n");
	  	avcodec_free_context(&codecCtx);
		return FAILURE;
	}

	int channels = codecCtx->ch_layout.nb_channels;
	if (channels <= 0) {
		channels = 2;
	}
	s->swr_out_channels = channels > 2 ? 2 : channels;

	s->audio_codec_ctx = codecCtx;
	s->audio_stream = stream_index;
    s->audio_st = stream;
    s->buffer_size = (s->init_audio_track_callback(s->clazz, codecCtx->sample_rate, s->swr_out_channels, from_thread));

	return SUCCESS;
}

static int player_prepare_state(State *state, int from_thread)
{
	int audio_index = -1;
	int video_index = -1;
	int i;

	if (!state) {
		return FAILURE;
	}
	
	if (state->pFormatCtx) {
		avformat_close_input(&state->pFormatCtx);
	}
	close_audio(state);

	state->pFormatCtx = NULL;
	state->audio_stream = -1;
	state->video_stream = -1;
	state->audio_st = NULL;
	state->video_st = NULL;

    printf("Path: %s\n", state->filename);

    AVDictionary *options = NULL;
    av_dict_set(&options, "user-agent", "FFmpegMediaPlayer", 0);
    av_dict_set(&options, "rw_timeout", "15000000", 0);
    av_dict_set(&options, "reconnect", "1", 0);
    av_dict_set(&options, "reconnect_streamed", "1", 0);
    av_dict_set(&options, "reconnect_on_network_error", "1", 0);
    av_dict_set(&options, "reconnect_delay_max", "30", 0);
    
    if (state->headers[0]) {
    	av_dict_set(&options, "headers", state->headers, 0);
    }
    
    // Allocate the context up front so the interrupt callback can abort a
    // blocking open/read when the player is reset.
    state->pFormatCtx = avformat_alloc_context();
    if (!state->pFormatCtx) {
        av_dict_free(&options);
        return FAILURE;
    }
    state->pFormatCtx->interrupt_callback.callback = decode_interrupt_cb;
    state->pFormatCtx->interrupt_callback.opaque = state;
    if (state->offset > 0) {
        state->pFormatCtx->skip_initial_bytes = state->offset;
    }
    
    int open_ret = avformat_open_input(&state->pFormatCtx, state->filename, NULL, &options);
    av_dict_free(&options);
    if (open_ret != 0) {
	    __android_log_print(ANDROID_LOG_ERROR, "TAG", "avformat_open_input failed: %d", open_ret);
	    if (!state->abort_request) state->notify_callback(state->clazz, MEDIA_ERROR, MEDIA_ERROR_UNKNOWN, open_ret, from_thread);
    	return FAILURE;
    }

	int info_ret = avformat_find_stream_info(state->pFormatCtx, NULL);
	if (info_ret < 0) {
	    __android_log_print(ANDROID_LOG_ERROR, "TAG", "avformat_find_stream_info failed: %d", info_ret);
	    avformat_close_input(&state->pFormatCtx);
	    if (!state->abort_request) state->notify_callback(state->clazz, MEDIA_ERROR, MEDIA_ERROR_UNKNOWN, info_ret, from_thread);
    	return FAILURE;
	}

	char duration[30] = "0";
	get_duration(state->pFormatCtx, duration);
	av_dict_set(&state->pFormatCtx->metadata, DURATION, duration, 0);
	
    // Find the first audio and video stream
	for (i = 0; i < state->pFormatCtx->nb_streams; i++) {
		if (state->pFormatCtx->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_VIDEO && video_index < 0) {
			video_index = i;
		}

		if (state->pFormatCtx->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_AUDIO && audio_index < 0) {
			audio_index = i;
		}
		
		set_codec(state->pFormatCtx, i);
	}

	if (audio_index >= 0) {
		stream_component_open(state, audio_index, from_thread);
	}

	if (video_index >= 0) {
		stream_component_open(state, video_index, from_thread);
	}

	if (state->audio_stream < 0) {
	    __android_log_print(ANDROID_LOG_ERROR, "TAG", "no playable audio stream");
	    avformat_close_input(&state->pFormatCtx);
	    close_audio(state);
	    if (!state->abort_request) state->notify_callback(state->clazz, MEDIA_ERROR, MEDIA_ERROR_UNKNOWN, -1, from_thread);
		return FAILURE;
	}

    // fill the inital buffer
	AVPacket *packet = av_packet_alloc();
	int ret;
    int bytes_written = 0;
	
	while (packet && state->audio_stream >= 0 && bytes_written < state->buffer_size) {
		ret = av_read_frame(state->pFormatCtx, packet);

		if (ret < 0) {
			break;
		}

		int frame_size_ptr = 0;
		decode_frame_from_packet(state, packet, &frame_size_ptr, from_thread);
		bytes_written = bytes_written + frame_size_ptr;
		av_packet_unref(packet);
	}

	av_packet_free(&packet);

	if (state->abort_request) {
		return FAILURE;
	}

	state->notify_callback(state->clazz, MEDIA_PREPARED, 0, 0, from_thread);

	return SUCCESS;
}

int player_prepare(State **ps, int from_thread)
{
	return player_prepare_state(*ps, from_thread);
}

int prepareAsync_l(State **ps)
{
     return SUCCESS;
}

int prepare(State **ps)
{
    printf("prepare\n");
    player_prepare(ps, NOT_FROM_THREAD);
    return SUCCESS;
}

void *player_prepare_thread(void *data)
{
	State *state = (State *) data;
	player_prepare_state(state, FROM_THREAD);
	state->preparing = 0;
	return NULL;
}

int prepareAsync(State **ps)
{
    State *state = *ps;
    if (!state) {
        return FAILURE;
    }
    // a previous prepare must be finished before this state is reused
    if (state->prepare_started) {
        state->abort_request = 1;
        pthread_join(state->prepare_thread, NULL);
        state->prepare_started = 0;
    }
    state->abort_request = 0;
    state->preparing = 1;
    if (pthread_create(&state->prepare_thread, NULL, player_prepare_thread, state) == 0) {
        state->prepare_started = 1;
    } else {
        state->preparing = 0;
    }
    return SUCCESS;
}

int start(State **ps)
{
	State *state = *ps;
    
    pthread_mutex_lock(lock);
	if (!state->decoder_started) {
		state->abort_request = 0;
		if (pthread_create(&state->decoder_thread, NULL, player_decode, state) == 0) {
			state->decoder_started = 1;
		}
	}
    state->paused = 0;
    pthread_mutex_unlock(lock);
    
    return 0;
}

int stop(State **ps)
{
	State *state = *ps;
	
    pthread_mutex_lock(lock);
    state->abort_request = 1;
    pthread_mutex_unlock(lock);
	
    return 0;
}

int pause(State **ps)
{
	State *state = *ps;
	
    pthread_mutex_lock(lock);
	state->paused = !state->paused;
    pthread_mutex_unlock(lock);
    
    return 0;
}

int isPlaying(State **ps)
{
	State *state = *ps;
	
	if (state->paused) {
		return 0;
	} else {
		return 1;
	}
}

int getVideoWidth(int *w)
{
    return 0;
}

int getVideoHeight(int *h)
{
    return 0;
}

int getCurrentPosition(State **ps, int *msec)
{
	State *state = *ps;
	if (!state) {
		*msec = 0;
		return 0;
	}
	*msec = state->audio_clock * 1000;
    return 0;
}

int getDuration(State **ps, int *msec)
{
	State *state = *ps;
	// pFormatCtx is only safe to read once preparing has finished
	if (state && !state->preparing && state->pFormatCtx
			&& (state->pFormatCtx->duration != AV_NOPTS_VALUE)) {
		*msec = (state->pFormatCtx->duration / AV_TIME_BASE) * 1000;
	} else {
		*msec = 0;
	}
	
    return 0;
}

int seekTo(State **ps, int msec)
{
	State *state = *ps;
	
    pthread_mutex_lock(lock);

    if (!state->seek_req) {
    	state->seek_pos = msec * 1000;
    	state->seek_rel = msec * 1000;
    	state->seek_flags = AVSEEK_FLAG_FRAME;
        state->seek_req = 1;
    }

    pthread_mutex_unlock(lock);
	
    return 0;
}

int reset(State **ps)
{
	State *state = *ps;
	
    pthread_mutex_lock(lock);

    stop_threads(state);
    
    pthread_mutex_unlock(lock);
	
    clear_l(ps);
    
    return 0;
}

int setLooping(State **ps, int loop)
{
	State *state = *ps;
	
	state->loop = loop;
	
    return 0;
}

/*void notify(int msg, int ext1, int ext2)
{

}*/

/*sp<IMemory> decode(const char* url, uint32_t *pSampleRate, int* pNumChannels, int* pFormat)
{
    return p;
}

sp<IMemory> decode(int fd, int64_t offset, int64_t length, uint32_t *pSampleRate, int* pNumChannels, int* pFormat)
{
    return p;
}*/

