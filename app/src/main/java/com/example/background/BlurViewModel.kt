/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.background

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.background.workers.BlurWorker
import com.example.background.workers.CleanupWorker
import com.example.background.workers.SaveImageToFileWorker

//BlurViewModel:* 이 뷰 모델은 BlurActivity를 표시하는 데 필요한 데이터를 모두 저장합니다. WorkManager를 사용하여 백그라운드 작업을 시작하는 클래스이기도 합니다.
class BlurViewModel(application: Application) : AndroidViewModel(application) {

    internal var imageUri: Uri? = null
    internal var outputUri: Uri? = null
    private val workManager = WorkManager.getInstance(application)//뷰모델에서 workmanager 객체 생성
    internal val outputWorkInfos: LiveData<List<WorkInfo>> = workManager.getWorkInfosByTagLiveData(TAG_OUTPUT) //작업목록중에서 특정 태그의 일을 관찰한다
    internal val progressWorkInfoItems: LiveData<List<WorkInfo>> = workManager.getWorkInfosByTagLiveData(TAG_PROGRESS) //진행률 감시
    init {
        // This transformation makes sure that whenever the current work Id changes the WorkInfo
        // the UI is listening to changes
    }
    //작업 취소
    internal fun cancelWork() {
        workManager.cancelUniqueWork(IMAGE_MANIPULATION_WORK_NAME)
    }

    /**
     * Create the WorkRequest to apply the blur and save the resulting image
     * @param blurLevel The amount to blur the image
     */
    //뷰모델에서 고유한 작업체인 생성 - 한개의 작업 체인에 여러개의 workrequest 들이 등록되고 교체 된다
    internal fun applyBlur(blurLevel: Int) {
        // Add WorkRequest to Cleanup temporary images
        var continuation = workManager
                //일시정지안할거면 beginwith 로??
                .beginUniqueWork( //작업 요청을 추가할 수 있는 메서드
                        IMAGE_MANIPULATION_WORK_NAME,  //이 작업 고유의 이름
                        ExistingWorkPolicy.REPLACE, //이전 작업 정책 - 새작업으로 교체된다
                        OneTimeWorkRequest.from(CleanupWorker::class.java) //한번만 실행할 workrequest에 corutine worker 로 생성된 클래스를 준다
                //  return new OneTimeWorkRequest.Builder(workerClass).build();
                )

        // Add WorkRequests to blur the image the number of times requested
        for (i in 0 until blurLevel) {
            val blurBuilder = OneTimeWorkRequestBuilder<BlurWorker>()//OneTimeWorkRequest.Builder(W::class.java)

            // Input the Uri if this is the first blur operation
            // After the first blur operation the input will be the output of previous
            // blur operations.
            if (i == 0) {
                //blur worker 에서 준 uri를 받아서 output 한다
                val data = workDataOf(KEY_IMAGE_URI to imageUri.toString())
                //work manager 의 결과물을 데이터 객체로 준다
                //입력데이터를 작업에 추가 한다
                blurBuilder.setInputData(data)
            }
            blurBuilder.addTag(TAG_PROGRESS)
            continuation = continuation.then(blurBuilder.build()) //then - 후자가 적용 되도록 클린워커 -> 블러워커로 교체
        }

        // Create charging constraint
        //Constraints - 작업을 실행시키기 전에 충족시켜야 하는 요구사항
        val constraints = Constraints.Builder()
                .setRequiresCharging(true)
                .build()

        // Add WorkRequest to save the image to the filesystem
        val save = OneTimeWorkRequestBuilder<SaveImageToFileWorker>()
                .setConstraints(constraints)
                .addTag(TAG_OUTPUT)
                .build()
        continuation = continuation.then(save)


        // Actually start the work 실제 백그라운드 스레드에 작업을 등록시키고 일합니다
        continuation.enqueue()
    }

    private fun uriOrNull(uriString: String?): Uri? {
        return if (!uriString.isNullOrEmpty()) {
            Uri.parse(uriString)
        } else {
            null
        }
    }

    /**
     * Setters
     */
    internal fun setImageUri(uri: String?) {
        imageUri = uriOrNull(uri)
    }

    internal fun setOutputUri(outputImageUri: String?) {
        outputUri = uriOrNull(outputImageUri)
    }
}
