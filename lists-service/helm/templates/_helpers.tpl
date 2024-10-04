{{/*
Expand the name of the chart.
*/}}
{{- define "ala-species-lists.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "ala-species-lists.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "ala-species-lists.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "ala-species-lists.labels" -}}
helm.sh/chart: {{ include "ala-species-lists.chart" . }}
{{ include "ala-species-lists.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
deployment annotations
checksum of the config
*/}}
{{- define "ala-species-lists.deploymentAnnotations" -}}
{{- $configChecksun := ( dict "checksum/lists-service-config.properties" ( .Files.Get "config/lists-service-config.properties" | sha256sum ) ) -}}
{{- $annotations := merge .Values.deploymentAnnotations $configChecksun -}}
{{ toYaml $annotations }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "ala-species-lists.selectorLabels" -}}
app.kubernetes.io/name: {{ include "ala-species-lists.fullname" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "ala-species-lists.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "ala-species-lists.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}
