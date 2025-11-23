Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Отправка изменений в KMP ветку" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "Проверка текущей ветки..." -ForegroundColor Yellow
$currentBranch = git branch --show-current
Write-Host "Текущая ветка: $currentBranch" -ForegroundColor Green
Write-Host ""

if ($currentBranch -ne "multiplatform_app_ttb") {
    Write-Host "ВНИМАНИЕ: Вы не на ветке multiplatform_app_ttb!" -ForegroundColor Red
    Write-Host "Переключение на multiplatform_app_ttb..." -ForegroundColor Yellow
    git checkout multiplatform_app_ttb
    Write-Host ""
}

Write-Host "Добавление всех изменений..." -ForegroundColor Yellow
git add .

Write-Host ""
Write-Host "Создание коммита..." -ForegroundColor Yellow
$commitMessage = @"
Обновление KMP ветки: добавлена структура, дизайн изменения, документация по iOS тестированию

- Добавлен shared модуль с KMP конфигурацией
- Обновлен дизайн drawer (закругления)
- Обновлен дизайн чата (уменьшенные баблы, полупрозрачность)
- Добавлена документация по тестированию на iOS
- Создана базовая KMP структура
"@

git commit -m $commitMessage

Write-Host ""
Write-Host "Отправка в удаленный репозиторий..." -ForegroundColor Yellow
git push -u origin multiplatform_app_ttb

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Готово! Изменения отправлены." -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan

