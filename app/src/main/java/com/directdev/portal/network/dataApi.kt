package com.directdev.portal.network

import android.content.Context
import com.directdev.portal.R
import com.directdev.portal.model.*
import com.directdev.portal.utils.NullConverterFactory
import com.directdev.portal.utils.readPref
import com.directdev.portal.utils.savePref
import com.facebook.stetho.okhttp3.StethoInterceptor
import io.realm.Realm
import io.realm.RealmObject
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory
import retrofit2.converter.moshi.MoshiConverterFactory
import rx.Single
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import java.util.concurrent.TimeUnit

object DataApi {
    var isActive = false
    private val baseUrl = "https://binusmaya.binus.ac.id/services/ci/index.php/"
    //TODO: (NOTE) Delete OkHttpClient if timeout takes too long
    private val api = Retrofit.Builder()
            .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
            .addConverterFactory(NullConverterFactory())
            .addConverterFactory(MoshiConverterFactory.create())
            .client(buildClient())
            .baseUrl(baseUrl)
            .build().create(DataService::class.java)


    fun fetchData(ctx: Context): Single<Unit> {
        isActive = true
        val cookie = ctx.readPref(R.string.cookie, "") as String
        return signIn(ctx, cookie).flatMap {
            it.headers().get("Set-Cookie")?.savePref(ctx, R.string.cookie)
            api.getTerms(cookie).subscribeOn(Schedulers.io())
        }.flatMap { terms ->
            val realm = Realm.getDefaultInstance()
            realm.executeTransaction { it.copyToRealmOrUpdate(terms) }
            realm.close()
            fetchRecent(ctx, cookie, terms[0].value.toString())
        }
    }


    private fun signIn(ctx: Context, cookie: String = "") = api.signIn(
            ctx.readPref(R.string.username, "") as String,
            ctx.readPref(R.string.password, "") as String,
            cookie)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())


    private fun fetchRecent(ctx: Context, cookie: String, term: String) = Single.zip(
            api.getFinances(cookie).subscribeOn(Schedulers.io()),
            api.getSessions(cookie).subscribeOn(Schedulers.io()),
            api.getExams(ExamRequestBody(term), cookie).subscribeOn(Schedulers.io()),
            api.getGrades(term.toString(), cookie).subscribeOn(Schedulers.io()),
            api.getProfile(cookie).subscribeOn(Schedulers.io()),
            api.getFinanceSummary(cookie).subscribeOn(Schedulers.io()),
            { finance, session, exam, grade, profile, financeSummary ->
                val realm = Realm.getDefaultInstance()
                realm.executeTransaction {
                    it.delete(JournalModel::class.java)
                    it.delete(ExamModel::class.java)
                    it.delete(FinanceModel::class.java)
                    it.delete(SessionModel::class.java)
                    it.copyToRealmOrUpdate(mapToJournal(exam, finance, session))
                    it.insertGrade(grade)
                    saveProfile(ctx, profile)
                    saveFinanceSummary(ctx, financeSummary)
                    profile.close()
                }
                realm.close()
                isActive = false
            })
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())

    private fun mapToJournal(exam: List<ExamModel>, finance: List<FinanceModel>, session: List<SessionModel>): MutableList<JournalModel> {
        val items = mutableListOf<JournalModel>()
        finance.forEach { items.add(JournalModel(it.dueDate).setDate()) }
        exam.forEach { items.add(JournalModel(it.date).setDate("yyyy-MM-dd")) }
        session.forEach { items.add(JournalModel(it.date).setDate()) }
        items.forEach { item ->
            session.forEach { if (item.id == it.date) item.session.add(it) }
            finance.forEach { if (item.id == it.dueDate) item.finance.add(it) }
            exam.forEach { if (item.id == it.date) item.exam.add(it) }
        }
        return items
    }

    private fun saveProfile(ctx: Context, response: ResponseBody) {
        val profile = JSONObject(response.string()).getJSONArray("Profile").getJSONObject(0)
        profile.getString("ACAD_PROG_DESCR").savePref(ctx, R.string.major)
        profile.getString("ACAD_CAREER_DESCR").savePref(ctx, R.string.degree)
        profile.getString("BIRTHDATE").savePref(ctx, R.string.birthday)
        profile.getString("NAMA").savePref(ctx, R.string.name)
        profile.getString("NIM").savePref(ctx, R.string.nim)
    }

    private fun saveFinanceSummary(ctx: Context, response: ResponseBody) {
        val summary = JSONArray(response.string()).getJSONObject(0)
        summary.getInt("charge").savePref(ctx, R.string.finance_charge)
        summary.getInt("payment").savePref(ctx, R.string.finance_payment)
    }

    private fun Realm.insertGrade(data: GradeModel) {
        data.credit.term = data.term
        cleanInsert(data.gradings)
        cleanInsert(data.scores)
        copyToRealmOrUpdate(data.credit)
    }

    private fun Realm.cleanInsert(data: List<RealmObject>) {
        if (data.size == 0) return
        delete(data[0].javaClass)
        copyToRealm(data)
    }

    private fun buildClient() = OkHttpClient().newBuilder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addNetworkInterceptor(StethoInterceptor())
            .followRedirects(false)
            .build()
}
