@Composable
private fun BatteryWarningCard(vm: FearViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1F17)),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "⚠️ 为保证桌面小组件每小时自动刷新，请允许后台运行",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFFF9F43),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "系统省电策略可能会冻结后台任务导致小组件停止更新。建议解除电池优化，并开启自启动。",
                fontSize = 11.sp,
                color = Muted,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { vm.requestIgnoreBattery() },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("解除电池优化", fontSize = 12.sp)
                }
                if (vm.isXiaomi) {
                    Button(
                        onClick = { vm.openAutoStart() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("开启自启动", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
