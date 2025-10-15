# Configuração do Google Cloud Storage para Upload de Fotos

## Problema Resolvido
O App Engine tem um sistema de ficheiros read-only, por isso não consegue escrever ficheiros localmente. A solução é usar o Google Cloud Storage.

## Passos para Configuração

### 1. Criar o Bucket no Google Cloud Console

#### Opção A: Via Google Cloud Console (Interface Web)
1. Aceda ao [Google Cloud Console](https://console.cloud.google.com/)
2. Vá para "Cloud Storage" > "Buckets"
3. Clique em "Create Bucket"
4. Configure:
   - **Nome**: `trailblaze-photos`
   - **Tipo de localização**: Region
   - **Região**: `europe-west1` (ou mais próxima dos utilizadores)
   - **Classe de armazenamento**: Standard
   - **Controlo de acesso**: Fine-grained
5. Clique "Create"

#### Opção B: Via Cloud Shell (Recomendado)
1. Abra o Cloud Shell no Google Cloud Console
2. Execute o script fornecido:
```bash
# Dar permissões de execução
chmod +x create-storage-bucket.sh

# Executar o script
./create-storage-bucket.sh
```

### 2. Configurar Permissões do Bucket
```bash
# Permitir leitura pública (necessário para servir as imagens)
gsutil iam ch allUsers:objectViewer gs://trailblaze-photos

# Verificar permissões
gsutil iam get gs://trailblaze-photos
```

### 3. Configurar CORS (se necessário)
```bash
# Criar ficheiro cors.json
echo '[
  {
    "origin": ["*"],
    "method": ["GET"],
    "responseHeader": ["Content-Type"],
    "maxAgeSeconds": 3600
  }
]' > cors.json

# Aplicar configuração CORS
gsutil cors set cors.json gs://trailblaze-photos

# Limpar ficheiro temporário
rm cors.json
```

### 4. Deploy da Aplicação
```bash
# Compilar e fazer deploy
mvn clean package
gcloud app deploy target/appengine-staging/app.yaml
```

## Como Funciona Agora

### Desenvolvimento Local
- As fotos são guardadas em `./uploads/photos`
- Servidas via endpoint `/rest/photos/view/{filename}`

### Produção (App Engine)
- As fotos são guardadas no Google Cloud Storage bucket `trailblaze-photos`
- URLs das fotos: `https://storage.googleapis.com/trailblaze-photos/{filename}`
- O endpoint `/rest/photos/view/{filename}` serve as fotos diretamente do Cloud Storage

## Verificação

### Testar Upload
1. Faça upload de uma foto através da aplicação
2. Verifique se a foto aparece no bucket via Cloud Console
3. Teste se a URL da foto funciona

### Verificar Logs
```bash
# Ver logs da aplicação
gcloud app logs tail -s default
```

### URLs de Exemplo
- Upload: `POST https://trailblaze-460312.oa.r.appspot.com/rest/photos/upload`
- Visualizar: `GET https://trailblaze-460312.oa.r.appspot.com/rest/photos/view/{filename}`
- Foto direta: `https://storage.googleapis.com/trailblaze-photos/{filename}`

## Resolução de Problemas

### Erro 403 (Forbidden)
- Verificar se o bucket tem permissões públicas de leitura
- Verificar se a aplicação tem permissões para escrever no bucket

### Erro 404 (Not Found)
- Verificar se o bucket existe
- Verificar se o nome do bucket está correto no código

### Erro de CORS
- Configurar CORS no bucket conforme instruções acima
- Verificar se as origens estão corretas

## Custos
- O Google Cloud Storage tem custos baseados em:
  - Armazenamento usado
  - Número de operações
  - Transferência de dados
- Para uso normal da aplicação, os custos devem ser mínimos
