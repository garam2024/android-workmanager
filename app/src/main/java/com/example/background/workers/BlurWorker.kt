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

package com.example.background.workers

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.background.KEY_IMAGE_URI
import com.example.background.data.BlurredImage
import com.example.background.data.ImagesDatabase

//백그라운드에서 실행시키고 싶은 기능 : 이미지 블러처리하기 -> Worker 클래스를 상속받고 실제작업 코드 입력
//workmanager 인스턴스를 만들기 위한 파라미터들을 넣어준다
class BlurWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        //Worker에 인자를 절달한다
        //전달받는 곳? Data 클래스 , 전달방식 : 키-쌍
        val resourceUri = inputData.getString(KEY_IMAGE_URI) //
        //작업알림을 줍니다? 몇초?
        makeStatusNotification("Blurring image", applicationContext)

        return try {
            //인자전달여부
            if (resourceUri.isNullOrEmpty()) {

                throw IllegalArgumentException("Invalid input uri")
            }

            val outputData = blurAndWriteImageToFile(resourceUri)
            recordImageSaved(resourceUri)
            //성공 결과는 Data 클래스를 통해 반환
            Result.success(outputData)
        } catch (throwable: Throwable) {

            Result.failure()
        }
    }
    //이미지를 실제 데이터베이스에 넣는 것일듯?
    private suspend fun recordImageSaved(resourceUri: String) {
        val imageDao = ImagesDatabase.getDatabase(applicationContext).blurredImageDao()
        imageDao.insert(BlurredImage(resourceUri))
    }

    private fun blurAndWriteImageToFile(resourceUri: String): Data {
        //데이터 읽기
        val resolver = applicationContext.contentResolver
        //bitmap 생성
        //접근클래스-갤러리에 있는 파일을 가져오기
        val picture = BitmapFactory.decodeStream(
            resolver.openInputStream(Uri.parse(resourceUri)) //문자열을 parse 해서 uri 객체로 만들기
        )
        //갤러리에서 가져온 비트맵이미지를 블러처리한다
        val output = blurBitmap(picture, applicationContext)

        // Write bitmap to a temp file
        //블러처리한 비트맵에 대한 uri 생성한다
        val outputUri = writeBitmapToFile(applicationContext, output)
        //KEY_IMAGE_URI가 키가 되고
        //전달해야하는 uri 객체가 값이 된다
        return workDataOf(KEY_IMAGE_URI to outputUri.toString())
    }
}
