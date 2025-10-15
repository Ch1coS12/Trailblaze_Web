#!/bin/bash

# Script para criar o bucket do Google Cloud Storage para fotos
# Execute este script através do Google Cloud Shell ou localmente com gcloud CLI

PROJECT_ID="trailblaze-460312"
BUCKET_NAME="trailblaze-photos"
REGION="europe-west1"  # Escolha a região mais próxima dos seus usuários

echo "Creating bucket $BUCKET_NAME in project $PROJECT_ID..."

# Criar o bucket
gsutil mb -p $PROJECT_ID -c STANDARD -l $REGION gs://$BUCKET_NAME

# Configurar permissões públicas para leitura (para servir as imagens)
gsutil iam ch allUsers:objectViewer gs://$BUCKET_NAME

# Configurar CORS para permitir acesso do frontend
echo '[
  {
    "origin": ["*"],
    "method": ["GET"],
    "responseHeader": ["Content-Type"],
    "maxAgeSeconds": 3600
  }
]' > cors.json

gsutil cors set cors.json gs://$BUCKET_NAME
rm cors.json

echo "Bucket $BUCKET_NAME created successfully!"
echo "Bucket URL: https://storage.googleapis.com/$BUCKET_NAME/"
echo ""
echo "Next steps:"
echo "1. Deploy your application with the updated code"
echo "2. Test photo upload functionality"
echo "3. Check the Cloud Console to verify photos are being stored"
