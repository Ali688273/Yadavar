package com.yadavar.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yadavar.app.core.BirthdayNotificationScheduler
import com.yadavar.app.core.BirthdayReminderEngine
import com.yadavar.app.core.StoredBirthday

data class TodoItem(val id:Int,val title:String,val done:Boolean=false)

class MainActivity:ComponentActivity(){
    private val permission=registerForActivityResult(ActivityResultContracts.RequestPermission()){}
    override fun onCreate(state:Bundle?){
        super.onCreate(state)
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent{YadavarApp(this)}
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YadavarApp(context:Context){
    var tab by remember{mutableIntStateOf(0)}
    var addTask by remember{mutableStateOf(false)}
    var addBirthday by remember{mutableStateOf(false)}
    var showDeleteCompleted by remember{mutableStateOf(false)}
    val tasks=remember{mutableStateListOf<TodoItem>().apply{addAll(loadTasks(context))}}
    val birthdays=remember{mutableStateListOf<StoredBirthday>().apply{addAll(loadBirthdays(context))}}

    fun saveT(){saveTasks(context,tasks)}
    fun saveB(){saveBirthdays(context,birthdays);BirthdayNotificationScheduler.scheduleAll(context,birthdays)}

    LaunchedEffect(Unit){BirthdayNotificationScheduler.scheduleAll(context,birthdays)}

    Scaffold(
        topBar={TopAppBar(title={Text(if(tab==0)"یادآور" else if(tab==1)"تولدها 🎂" else "تنظیمات",fontWeight=FontWeight.Bold)})},
        bottomBar={NavigationBar{
            NavigationBarItem(tab==0,{tab=0},{Icon(Icons.Default.Checklist,"کارها")},{Text("کارها")})
            NavigationBarItem(tab==1,{tab=1},{Icon(Icons.Default.Cake,"تولدها")},{Text("تولدها")})
            NavigationBarItem(tab==2,{tab=2},{Icon(Icons.Default.Settings,"تنظیمات")},{Text("تنظیمات")})
        }},
        floatingActionButton={if(tab<2)FloatingActionButton(onClick={if(tab==0)addTask=true else addBirthday=true}){Icon(Icons.Default.Add,"افزودن")}}
    ){pad->
        when(tab){
            0->TodoScreen(
                tasks=tasks,
                toggle={id->val i=tasks.indexOfFirst{it.id==id};if(i>=0){tasks[i]=tasks[i].copy(done=!tasks[i].done);saveT()}},
                delete={id->tasks.removeAll{it.id==id};saveT()},
                onRequestClear={showDeleteCompleted=true},
                modifier=Modifier.padding(pad)
            )
            1->BirthdayScreen(birthdays,{b->BirthdayNotificationScheduler.cancelBirthday(context,b.month,b.day);birthdays.removeAll{it.id==b.id};saveB()},Modifier.padding(pad))
            else->SettingsScreen(tasks.size,tasks.count{it.done},birthdays.size,Modifier.padding(pad))
        }
    }
    if(showDeleteCompleted)AlertDialog(
        onDismissRequest={showDeleteCompleted=false},
        title={Text("حذف کارهای انجام‌شده")},
        text={Text("همه کارهای انجام‌شده حذف شوند؟")},
        confirmButton={Button(onClick={tasks.removeAll{it.done};saveT();showDeleteCompleted=false}){Text("حذف")}},
        dismissButton={TextButton(onClick={showDeleteCompleted=false}){Text("انصراف")}}
    )
    if(addTask)AddTaskDialog({addTask=false}){title->if(title.trim().isNotEmpty()){tasks.add(TodoItem((tasks.maxOfOrNull{it.id}?:0)+1,title.trim()));saveT()};addTask=false}
    if(addBirthday)AddBirthdayDialog({addBirthday=false}){name,m,d->
        if(name.trim().isNotEmpty()&&m in 1..12&&d in 1..31){
            val b=StoredBirthday((birthdays.maxOfOrNull{it.id}?:0)+1,name.trim(),m,d);birthdays.add(b);saveB()
        };addBirthday=false
    }
}

@Composable
fun TodoScreen(tasks:List<TodoItem>,toggle:(Int)->Unit,delete:(Int)->Unit,onRequestClear:()->Unit,modifier:Modifier=Modifier){
    val done=tasks.count{it.done}
    Column(modifier.fillMaxSize().padding(16.dp)){
        Text("کارهای امروز",fontSize=26.sp,fontWeight=FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(if(tasks.isEmpty())"هنوز کاری ثبت نشده است." else done.toString()+" از "+tasks.size+" کار انجام شده",color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(done>0)TextButton(onClick=onRequestClear){Icon(Icons.Default.RemoveDone,null);Spacer(Modifier.width(4.dp));Text("حذف انجام‌شده‌ها")}
        if(tasks.isEmpty())Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
            Icon(Icons.Default.CheckCircle,null);Spacer(Modifier.height(10.dp));Text("برای شروع روی + بزنید.")
        }else LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)){items(tasks,key={it.id}){t->
            Card(Modifier.fillMaxWidth()){Row(Modifier.fillMaxWidth().padding(10.dp),verticalAlignment=Alignment.CenterVertically){
                Checkbox(t.done,{toggle(t.id)});Text(t.title,Modifier.weight(1f).padding(horizontal=6.dp),fontSize=17.sp);IconButton({delete(t.id)}){Icon(Icons.Default.Delete,"حذف")}
            }}
        }}
    }
}

@Composable
fun AddTaskDialog(dismiss:()->Unit,add:(String)->Unit){
    var title by remember{mutableStateOf("")}
    AlertDialog(onDismissRequest=dismiss,title={Text("کار جدید")},text={OutlinedTextField(title,{title=it},Modifier.fillMaxWidth(),singleLine=true,label={Text("عنوان کار")})},
        confirmButton={Button({add(title)},enabled=title.trim().isNotEmpty()){Text("افزودن")}},dismissButton={TextButton(dismiss){Text("انصراف")}})
}

@Composable
fun BirthdayScreen(list:List<StoredBirthday>,delete:(StoredBirthday)->Unit,modifier:Modifier=Modifier){
    Column(modifier.fillMaxSize().padding(16.dp)){
        Text("تولد عزیزان",fontSize=26.sp,fontWeight=FontWeight.Bold)
        Text("یک روز قبل و روز تولد اعلان ارسال می‌شود.",color=MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))
        if(list.isEmpty())Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
            Icon(Icons.Default.Cake,null);Spacer(Modifier.height(10.dp));Text("اولین تولد را با + اضافه کنید 🎂")
        }else LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)){items(list,key={it.id}){b->
            val r=BirthdayReminderEngine.reminder(b.name,b.month,b.day)
            Card(Modifier.fillMaxWidth()){Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){
                Icon(Icons.Default.Cake,null);Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){
                    Text(b.name,fontSize=18.sp,fontWeight=FontWeight.Bold)
                    Text("تاریخ: "+b.day+"/"+b.month,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if(r.isToday)"امروز تولدشه 🎉" else r.daysUntil.toString()+" روز تا تولد")
                };IconButton({delete(b)}){Icon(Icons.Default.Delete,"حذف")}
            }}
        }}
    }
}

@Composable
fun AddBirthdayDialog(dismiss:()->Unit,add:(String,Int,Int)->Unit){
    var name by remember{mutableStateOf("")}
    var day by remember{mutableStateOf("")}
    var month by remember{mutableStateOf("")}
    var error by remember{mutableStateOf("")}
    val d=day.toIntOrNull()
    val m=month.toIntOrNull()
    AlertDialog(
        onDismissRequest=dismiss,
        title={Text("افزودن تولد")},
        text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedTextField(name,{name=it},Modifier.fillMaxWidth(),singleLine=true,label={Text("نام")})
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedTextField(day,{day=it.filter(Char::isDigit)},Modifier.weight(1f),singleLine=true,label={Text("روز")})
                OutlinedTextField(month,{month=it.filter(Char::isDigit)},Modifier.weight(1f),singleLine=true,label={Text("ماه")})
            }
            Text("مثال: 15 / 7",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(error.isNotEmpty())Text(error,color=MaterialTheme.colorScheme.error)
        }},
        confirmButton={Button(onClick={when{
            name.isBlank()->error="نام را وارد کنید"
            m !in 1..12->error="ماه باید بین 1 تا 12 باشد"
            d !in 1..31->error="روز باید بین 1 تا 31 باشد"
            else->{add(name,m!!,d!!);dismiss()}
        }}){Text("ذخیره")}},
        dismissButton={TextButton(dismiss){Text("انصراف")}}
    )
}

@Composable
fun SettingsScreen(total:Int,done:Int,birthdays:Int,modifier:Modifier=Modifier){
    Column(modifier.fillMaxSize().padding(20.dp)){
        Text("تنظیمات",fontSize=25.sp,fontWeight=FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){
            Text("آمار",fontWeight=FontWeight.Bold);Spacer(Modifier.height(8.dp))
            Text("کل کارها: "+total);Text("انجام‌شده: "+done);Text("باقی‌مانده: "+(total-done));Text("تولدهای ثبت‌شده: "+birthdays)
        }}
        Spacer(Modifier.height(20.dp));Text("اعلان تولد یک روز قبل و روز تولد فعال است.")
        Spacer(Modifier.height(20.dp));Text("نسخه 1.1.0")
    }
}

private fun loadTasks(c:Context):List<TodoItem>{
    val raw=c.getSharedPreferences("yadavar_data",0).getString("tasks",null)?:return emptyList()
    return raw.split("
").mapNotNull{p->val x=p.split("	",limit=3);if(x.size!=3)null else{val id=x[0].toIntOrNull();if(id==null)null else TodoItem(id,x[2],x[1]=="1")}}
}
private fun saveTasks(c:Context,list:List<TodoItem>){
    c.getSharedPreferences("yadavar_data",0).edit().putString("tasks",list.joinToString("
"){it.id.toString()+"	"+if(it.done)"1"else"0"+"	"+it.title.replace("
"," ").replace("	"," ")}).apply()
}
private fun loadBirthdays(c:Context):List<StoredBirthday>{
    val raw=c.getSharedPreferences("yadavar_data",0).getString("birthdays",null)?:return emptyList()
    return raw.split("
").mapNotNull{p->val x=p.split("	",limit=4);if(x.size!=4)null else{val id=x[0].toIntOrNull();val m=x[2].toIntOrNull();val d=x[3].toIntOrNull();if(id==null||m==null||d==null||m !in 1..12||d !in 1..31)null else StoredBirthday(id,x[1],m,d)}}
}
private fun saveBirthdays(c:Context,list:List<StoredBirthday>){
    c.getSharedPreferences("yadavar_data",0).edit().putString("birthdays",list.joinToString("
"){it.id.toString()+"	"+it.name.replace("
"," ").replace("	"," ")+"	"+it.month+"	"+it.day}).apply()
}