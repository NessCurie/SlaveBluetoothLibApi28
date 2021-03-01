## SlaveBluetoothLibApi28
android 从机蓝牙在api28时的一个方便实现的类库

For more information please see:  
[Android 蓝牙从机模式实现概述](https://nesscurie.github.io/2020/11/26/%E8%93%9D%E7%89%99/Android%20%E8%93%9D%E7%89%99%E4%BB%8E%E6%9C%BA%E6%A8%A1%E5%BC%8F%E5%AE%9E%E7%8E%B0%E6%A6%82%E8%BF%B0/)  
[Android从机模式的蓝牙音乐的实现](https://nesscurie.github.io/2020/11/27/%E8%93%9D%E7%89%99/Android%E4%BB%8E%E6%9C%BA%E6%A8%A1%E5%BC%8F%E7%9A%84%E8%93%9D%E7%89%99%E9%9F%B3%E4%B9%90%E7%9A%84%E5%AE%9E%E7%8E%B0/)  
[Android从机模式的蓝牙电话的实现](https://nesscurie.github.io/2020/11/28/%E8%93%9D%E7%89%99/Android%E4%BB%8E%E6%9C%BA%E6%A8%A1%E5%BC%8F%E7%9A%84%E8%93%9D%E7%89%99%E7%94%B5%E8%AF%9D%E7%9A%84%E5%AE%9E%E7%8E%B0/)  
[Android通过蓝牙获取通讯录和通话记录相关的实现](https://nesscurie.github.io/2020/12/02/%E8%93%9D%E7%89%99/Android%E9%80%9A%E8%BF%87%E8%93%9D%E7%89%99%E8%8E%B7%E5%8F%96%E9%80%9A%E8%AE%AF%E5%BD%95%E5%92%8C%E9%80%9A%E8%AF%9D%E8%AE%B0%E5%BD%95%E7%9B%B8%E5%85%B3%E7%9A%84%E5%AE%9E%E7%8E%B0/)  

## Download
Add this in your root build.gradle file (not your module build.gradle file):
<pre><code>allprojects {
    repositories {
        maven { url 'https://jitpack.io' }
    }
}
</code></pre>

Then, add the library to your module build.gradle
<pre><code>dependencies {
    compile 'com.github.NessCurie:SlaveBluetoothLibApi28:latest.release.here'
}
</code></pre>

such as release is 1.0

you can use:
<pre><code>dependencies {
    compile 'com.github.NessCurie:SlaveBluetoothLibApi28:1.0'
}
</code></pre>