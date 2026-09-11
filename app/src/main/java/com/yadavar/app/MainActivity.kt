package com.yadavar.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview

data class TodoItem(
    val id: Int,
    val title: String,
    val done: Boolean = false
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            YadavarApp()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YadavarApp() {

    var selectedTab by remember {
        mutableIntStateOf(0)
    }

    val tasks = remember {
        mutableStateListOf(
            TodoItem(1, "خرید روزانه"),
            TodoItem(2, "تماس با دوست"),
            TodoItem(3, "انجام کارهای امروز")
        )
    }

    MaterialTheme {

        Scaffold(

            topBar = {

                TopAppBar(
                    title = {
                        Text(
                            text = when (selectedTab) {
                                0 -> "یادآور"
                                1 -> "تولدها 🎂"
                                else -> "تنظیمات"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                )
            },

            bottomBar = {

                NavigationBar {

                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = {
                            selectedTab = 0
                        },
                        icon = {
                            Icon(
                                Icons.Default.Checklist,
                                contentDescription = "چک لیست"
                            )
                        },
                        label = {
                            Text("کارها")
                        }
                    )

                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = {
                            selectedTab = 1
                        },
                        icon = {
                            Icon(
                                Icons.Default.Cake,
                                contentDescription = "تولدها"
                            )
                        },
                        label = {
                            Text("تولدها")
                        }
                    )

                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = {
                            selectedTab = 2
                        },
                        icon = {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = "تنظیمات"
                            )
                        },
                        label = {
                            Text("تنظیمات")
                        }
                    )
                }
            },

            floatingActionButton = {

                if (selectedTab == 0) {

                    FloatingActionButton(
                        onClick = {

                            val newId =
                                (tasks.maxOfOrNull { it.id } ?: 0) + 1

                            tasks.add(
                                TodoItem(
                                    newId,
                                    "کار جدید"
                                )
                            )
                        }
                    ) {

                        Icon(
                            Icons.Default.Add,
                            contentDescription = "افزودن"
                        )
                    }
                }
            }

        ) { paddingValues ->

            when (selectedTab) {

                0 -> {

                    TodoScreen(
                        tasks = tasks,
                        onToggle = { id ->

                            val index =
                                tasks.indexOfFirst {
                                    it.id == id
                                }

                            if (index >= 0) {

                                val item = tasks[index]

                                tasks[index] =
                                    item.copy(
                                        done = !item.done
                                    )
                            }
                        },

                        onDelete = { id ->

                            tasks.removeAll {
                                it.id == id
                            }
                        },

                        modifier = Modifier
                            .padding(paddingValues)
                    )
                }

                1 -> {

                    BirthdayScreen(
                        modifier = Modifier
                            .padding(paddingValues)
                    )
                }

                2 -> {

                    SettingsScreen(
                        modifier = Modifier
                            .padding(paddingValues)
                    )
                }
            }
        }
    }
}

@Composable
fun TodoScreen(
    tasks: List<TodoItem>,
    onToggle: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    modifier: Modifier = Modifier
) {

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Text(
            text = "کارهای امروز",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        LazyColumn(
            verticalArrangement =
                Arrangement.spacedBy(10.dp)
        ) {

            items(
                items = tasks,
                key = { it.id }
            ) { task ->

                TodoCard(
                    task = task,
                    onToggle = {
                        onToggle(task.id)
                    },
                    onDelete = {
                        onDelete(task.id)
                    }
                )
            }
        }
    }
}

@Composable
fun TodoCard(
    task: TodoItem,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {

    Card(
        modifier = Modifier.fillMaxWidth(),

        elevation = CardDefaults
            .cardElevation(3.dp)
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),

            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Checkbox(
                checked = task.done,
                onCheckedChange = {
                    onToggle()
                }
            )

            Text(
                text = task.title,
                modifier = Modifier.weight(1f),
                fontSize = 17.sp
            )

            IconButton(
                onClick = onDelete
            ) {

                Icon(
                    Icons.Default.Delete,
                    contentDescription = "حذف"
                )
            }
        }
    }
}

@Composable
fun BirthdayScreen(
    modifier: Modifier = Modifier
) {

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),

        horizontalAlignment =
            Alignment.CenterHorizontally,

        verticalArrangement =
            Arrangement.Center
    ) {

        Icon(
            Icons.Default.Cake,
            contentDescription = null
        )

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        Text(
            text = "تولد عزیزانت",
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        Text(
            text = "به‌زودی امکان ثبت تولدها و یادآوری خودکار اضافه می‌شود."
        )
    }
}

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier
) {

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {

        Text(
            text = "تنظیمات",
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        Text(
            text = "نسخه 1.0.0"
        )

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        Text(
            text = "یادآور | چک‌لیست و یادآوری تولد عزیزان"
        )
    }
}

@Preview(
    showBackground = true
)
@Composable
fun PreviewYadavar() {

    YadavarApp()
}
