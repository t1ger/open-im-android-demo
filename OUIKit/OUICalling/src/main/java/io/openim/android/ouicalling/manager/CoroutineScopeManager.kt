package io.openim.android.ouicalling.manager

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import com.github.ajalt.timberkt.Timber

/**
 * 协程作用域管理器
 * 专门负责协程的创建、取消和生命周期管理
 */
class CoroutineScopeManager {
    
    private val scopes = mutableListOf<CoroutineScope>()
    
    /**
     * 构建新的协程作用域
     * @return 新的CoroutineScope
     */
    fun buildScope(): CoroutineScope {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scopes.add(scope)
        
        Timber.v { "[CoroutineScopeManager] 创建新的协程作用域，当前总数: ${scopes.size}" }
        return scope
    }
    
    /**
     * 取消指定的协程作用域
     * @param scope 要取消的作用域
     */
    fun scopeCancel(scope: CoroutineScope) {
        try {
            scope.cancel()
            scopes.remove(scope)
            
            Timber.v { "[CoroutineScopeManager] 取消协程作用域，剩余数量: ${scopes.size}" }
            
        } catch (e: Exception) {
            Timber.e(e) { "[CoroutineScopeManager] 取消协程作用域异常" }
        }
    }
    
    /**
     * 订阅Flow并自动管理协程生命周期
     * @param flow 要订阅的Flow
     * @param function 处理函数
     * @param scope 可选的作用域，默认创建新的
     */
    @JvmOverloads
    fun <T> subscribe(
        flow: Flow<T>, 
        function: (T) -> Any,
        scope: CoroutineScope = buildScope()
    ) {
        try {
            if (!scopes.contains(scope)) {
                scopes.add(scope)
            }
            
            scope.launch {
                flow.collect {
                    function.invoke(it)
                }
            }
            
            Timber.v { "[CoroutineScopeManager] 订阅Flow成功" }
            
        } catch (e: Exception) {
            Timber.e(e) { "[CoroutineScopeManager] 订阅Flow异常" }
        }
    }
    
    /**
     * 获取当前活跃的作用域数量
     */
    fun getActiveScopeCount(): Int {
        return scopes.count { it.isActive }
    }
    
    /**
     * 获取总作用域数量
     */
    fun getTotalScopeCount(): Int {
        return scopes.size
    }
    
    /**
     * 清理所有非活跃的作用域
     */
    fun cleanupInactiveScopes() {
        try {
            val inactiveScopes = scopes.filter { !it.isActive }
            scopes.removeAll(inactiveScopes)
            
            Timber.d { "[CoroutineScopeManager] 清理了 ${inactiveScopes.size} 个非活跃作用域，剩余: ${scopes.size}" }
            
        } catch (e: Exception) {
            Timber.e(e) { "[CoroutineScopeManager] 清理非活跃作用域异常" }
        }
    }
    
    /**
     * 取消所有作用域
     */
    fun cancelAllScopes() {
        try {
            val totalScopes = scopes.size
            scopes.toList().forEach { scope ->
                scope.cancel()
            }
            scopes.clear()
            
            Timber.d { "[CoroutineScopeManager] 取消了所有作用域: $totalScopes 个" }
            
        } catch (e: Exception) {
            Timber.e(e) { "[CoroutineScopeManager] 取消所有作用域异常" }
        }
    }
    
    /**
     * 获取作用域统计信息
     */
    fun getScopeStatistics(): ScopeStatistics {
        return ScopeStatistics(
            totalScopes = scopes.size,
            activeScopes = scopes.count { it.isActive },
            inactiveScopes = scopes.count { !it.isActive }
        )
    }
    
    /**
     * 清理资源
     */
    fun release() {
        try {
            Timber.d { "[CoroutineScopeManager] 清理协程作用域管理器资源" }
            cancelAllScopes()
        } catch (e: Exception) {
            Timber.e(e) { "[CoroutineScopeManager] 清理资源异常" }
        }
    }
}

/**
 * 作用域统计信息
 */
data class ScopeStatistics(
    val totalScopes: Int,
    val activeScopes: Int,
    val inactiveScopes: Int
)