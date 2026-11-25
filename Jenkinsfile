pipeline {
    /*
     * Вариант 14: анализ времени выполнения этапов Pipeline.
     * Для каждого основного этапа фиксируется длительность (мс/с),
     * итог записывается в stage-timings.txt и архивируется.
     */
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    stages {

        stage('Checkout') {
            steps {
                script {
                    def t0 = System.currentTimeMillis()
                    echo 'Получение исходного кода из Git'
                    checkout scm
                    def dt = System.currentTimeMillis() - t0
                    env.TIME_CHECKOUT_MS = "${dt}"
                    echo ">>> [TIMING] Checkout: ${dt} ms (${String.format('%.2f', dt / 1000.0)} s)"
                }
            }
        }

        stage('Toolchain') {
            steps {
                script {
                    def t0 = System.currentTimeMillis()
                    echo 'Проверка Java'
                    bat 'java -version'
                    echo 'Проверка Maven'
                    bat 'mvn -version'
                    echo 'Проверка Git'
                    bat 'git --version'
                    def dt = System.currentTimeMillis() - t0
                    env.TIME_TOOLCHAIN_MS = "${dt}"
                    echo ">>> [TIMING] Toolchain: ${dt} ms (${String.format('%.2f', dt / 1000.0)} s)"
                }
            }
        }

        stage('Build and Verify') {
            steps {
                script {
                    def t0 = System.currentTimeMillis()
                    echo 'Запуск полной проверки проекта (mvn clean verify)'
                    bat 'mvn -B -ntp clean verify'
                    def dt = System.currentTimeMillis() - t0
                    env.TIME_BUILD_MS = "${dt}"
                    echo ">>> [TIMING] Build and Verify: ${dt} ms (${String.format('%.2f', dt / 1000.0)} s)"
                }
            }
        }

        stage('Generate Reports') {
            steps {
                script {
                    def t0 = System.currentTimeMillis()
                    echo 'Формирование HTML-отчёта Surefire'
                    bat 'mvn -B -ntp surefire-report:report-only'
                    echo 'Формирование HTML-отчёта JaCoCo'
                    bat 'mvn -B -ntp jacoco:report'
                    def dt = System.currentTimeMillis() - t0
                    env.TIME_REPORTS_MS = "${dt}"
                    echo ">>> [TIMING] Generate Reports: ${dt} ms (${String.format('%.2f', dt / 1000.0)} s)"
                }
            }
        }

        /*
         * Итоговый анализ: сводка по всем этапам + запись в файл.
         */
        stage('Timing Analysis') {
            steps {
                script {
                    def c = (env.TIME_CHECKOUT_MS ?: '0') as long
                    def t = (env.TIME_TOOLCHAIN_MS ?: '0') as long
                    def b = (env.TIME_BUILD_MS ?: '0') as long
                    def r = (env.TIME_REPORTS_MS ?: '0') as long
                    def total = c + t + b + r

                    def lines = []
                    lines << '========================================'
                    lines << '  STAGE TIMING ANALYSIS (variant 14)'
                    lines << '========================================'
                    lines << "Checkout:          ${c} ms  (${String.format('%.2f', c / 1000.0)} s)"
                    lines << "Toolchain:         ${t} ms  (${String.format('%.2f', t / 1000.0)} s)"
                    lines << "Build and Verify:  ${b} ms  (${String.format('%.2f', b / 1000.0)} s)"
                    lines << "Generate Reports:  ${r} ms  (${String.format('%.2f', r / 1000.0)} s)"
                    lines << '----------------------------------------'
                    lines << "TOTAL (sum):       ${total} ms  (${String.format('%.2f', total / 1000.0)} s)"
                    lines << '========================================'
                    lines << ''
                    lines << 'Notes:'
                    lines << '- Build and Verify usually takes most of the time'
                    lines << '  (dependencies + compile + unit/IT tests).'
                    lines << '- First build on agent is slower (cold Maven cache).'
                    lines << '- Later builds are faster due to local .m2 cache.'

                    def report = lines.join('\n')
                    echo report
                    writeFile file: 'stage-timings.txt', text: report, encoding: 'UTF-8'

                    def maxMs = Math.max(Math.max(c, t), Math.max(b, r))
                    def maxName = (maxMs == b) ? 'Build and Verify'
                                : (maxMs == r) ? 'Generate Reports'
                                : (maxMs == t) ? 'Toolchain' : 'Checkout'
                    echo ">>> [TIMING] Longest stage: ${maxName} (${maxMs} ms, ${String.format('%.1f', 100.0 * maxMs / Math.max(total, 1))}% of total)"
                }
            }
        }
    }

    post {
        always {
            junit(
                allowEmptyResults: true,
                testResults: 'target/surefire-reports/TEST-*.xml,target/failsafe-reports/TEST-*.xml'
            )
            archiveArtifacts(
                artifacts: 'target/surefire-reports/**,target/failsafe-reports/**,target/site/**,stage-timings.txt',
                allowEmptyArchive: true
            )
        }
        success {
            archiveArtifacts(
                artifacts: 'target/task-manager-cicd-1.0.0.jar',
                fingerprint: true
            )
        }
        failure {
            echo 'Pipeline failed. Check Console Output and stage-timings.txt if available.'
        }
        unstable {
            echo 'Pipeline is UNSTABLE. Check test results.'
        }
    }
}
