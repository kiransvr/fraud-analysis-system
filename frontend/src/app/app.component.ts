import { CommonModule } from '@angular/common';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { environment } from '../environments/environment';

interface TransactionResponse {
  externalTransactionId: string;
  amount: number;
  currencyCode: string;
  status: string;
  fraudProbability: number | null;
  riskLevel: string | null;
  modelVersion: string | null;
  alertId: number | null;
}

interface AlertResponse {
  id: number;
  externalTransactionId: string;
  alertType: string;
  severity: string;
  status: string;
  reason: string;
}

interface BulkUploadError {
  rowNumber: number;
  externalTransactionId: string | null;
  message: string;
}

interface BulkUploadResponse {
  totalRows: number;
  processedRows: number;
  failedRows: number;
  alertsCreated: number;
  errors: BulkUploadError[];
}

interface CountResponse {
  total: number;
}

@Component({
  selector: 'app-root',
  imports: [CommonModule, FormsModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent implements OnInit, OnDestroy {
  private readonly apiBase = environment.apiBaseUrl;
  private readonly fallbackRefreshMs = 15000;
  private readonly sseReconnectMs = 3000;
  private autoRefreshTimer: ReturnType<typeof setInterval> | null = null;
  private sseReconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private eventSource: EventSource | null = null;

  transactions: TransactionResponse[] = [];
  alerts: AlertResponse[] = [];
  totalTransactionsCount = 0;
  isLoading = false;
  isSubmitting = false;
  isUploading = false;
  selectedCsvFile: File | null = null;
  replaceExistingOnUpload = true;
  liveUpdatesEnabled = true;
  liveConnectionMode: 'push' | 'polling' | 'offline' = 'offline';
  lastUpdatedAt = '';
  errorMessage = '';
  successMessage = '';

  transactionForm = {
    externalTransactionId: `tx-ui-${Date.now()}`,
    customerId: 'cust-ui-001',
    deviceId: 'dev-ui-001',
    amount: 1500,
    currencyCode: 'USD',
    merchantId: 'merchant-ui-01',
    merchantCategory: 'electronics',
    channel: 'web',
    countryCode: 'US'
  };

  constructor(private readonly http: HttpClient) {}

  ngOnInit(): void {
    this.refreshDashboard();
    this.startLiveUpdates();
  }

  ngOnDestroy(): void {
    this.stopLiveUpdates();
  }

  refreshDashboard(): void {
    this.errorMessage = '';
    this.isLoading = true;

    const params = new HttpParams().set('limit', '20').set('offset', '0');

    this.http.get<TransactionResponse[]>(`${this.apiBase}/transactions`, { params }).subscribe({
      next: (transactions) => {
        this.transactions = transactions;
        this.isLoading = false;
        this.lastUpdatedAt = new Date().toLocaleTimeString();
      },
      error: (error: HttpErrorResponse) => {
        this.isLoading = false;
        this.errorMessage = this.extractError(error, 'Unable to load transactions.');
      }
    });

    this.http.get<CountResponse>(`${this.apiBase}/transactions/count`).subscribe({
      next: (countResponse) => {
        this.totalTransactionsCount = countResponse.total;
      },
      error: () => {
        this.totalTransactionsCount = this.transactions.length;
      }
    });

    this.http.get<AlertResponse[]>(`${this.apiBase}/alerts`).subscribe({
      next: (alerts) => {
        this.alerts = alerts;
      },
      error: (error: HttpErrorResponse) => {
        this.errorMessage = this.extractError(error, 'Unable to load alerts.');
      }
    });
  }

  toggleLiveUpdates(): void {
    this.liveUpdatesEnabled = !this.liveUpdatesEnabled;
    if (this.liveUpdatesEnabled) {
      this.startLiveUpdates();
      this.refreshDashboard();
      return;
    }
    this.stopLiveUpdates();
  }

  submitTransaction(): void {
    this.successMessage = '';
    this.errorMessage = '';
    this.isSubmitting = true;

    this.http.post<TransactionResponse>(`${this.apiBase}/transactions`, this.transactionForm).subscribe({
      next: (response) => {
        this.isSubmitting = false;
        this.successMessage = `Scored ${response.externalTransactionId} as ${response.riskLevel}.`;
        this.transactionForm.externalTransactionId = `tx-ui-${Date.now()}`;
        this.refreshDashboard();
      },
      error: (error: HttpErrorResponse) => {
        this.isSubmitting = false;
        this.errorMessage = this.extractError(error, 'Transaction submission failed.');
      }
    });
  }

  onCsvSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedCsvFile = input.files && input.files.length > 0 ? input.files[0] : null;
  }

  uploadCsv(): void {
    if (!this.selectedCsvFile) {
      this.errorMessage = 'Please choose a CSV file before uploading.';
      return;
    }

    this.successMessage = '';
    this.errorMessage = '';
    this.isUploading = true;

    const formData = new FormData();
    formData.append('file', this.selectedCsvFile);

    const params = new HttpParams().set('replaceExisting', String(this.replaceExistingOnUpload));

    this.http.post<BulkUploadResponse>(`${this.apiBase}/transactions/upload`, formData, { params }).subscribe({
      next: (response) => {
        this.isUploading = false;
        this.selectedCsvFile = null;

        const firstError = response.errors.length > 0 ? ` First error: ${response.errors[0].message}` : '';
        this.successMessage = `Uploaded ${response.processedRows}/${response.totalRows} rows. Failed: ${response.failedRows}. Alerts: ${response.alertsCreated}.${firstError}`;
        this.refreshDashboard();
      },
      error: (error: HttpErrorResponse) => {
        this.isUploading = false;
        this.errorMessage = this.extractError(error, 'CSV upload failed.');
      }
    });
  }

  highRiskCount(): number {
    return this.transactions.filter((tx) => tx.riskLevel === 'HIGH').length;
  }

  avgProbability(): string {
    const withScores = this.transactions.filter((tx) => tx.fraudProbability !== null);
    if (withScores.length === 0) {
      return '0.00';
    }

    const sum = withScores.reduce((acc, tx) => acc + (tx.fraudProbability ?? 0), 0);
    return (sum / withScores.length).toFixed(2);
  }

  private extractError(error: HttpErrorResponse, fallback: string): string {
    const message = error.error?.message;
    if (typeof message === 'string' && message.trim().length > 0) {
      return message;
    }
    return fallback;
  }

  private startLiveUpdates(): void {
    this.stopLiveUpdates();

    this.connectEventStream();
    this.autoRefreshTimer = setInterval(() => {
      if (!this.liveUpdatesEnabled || this.isSubmitting || this.isUploading) {
        return;
      }
      this.refreshDashboard();
    }, this.fallbackRefreshMs);
  }

  private stopLiveUpdates(): void {
    if (!this.autoRefreshTimer) {
      this.clearSseReconnectTimer();
      this.closeEventStream();
      this.liveConnectionMode = 'offline';
      return;
    }
    clearInterval(this.autoRefreshTimer);
    this.autoRefreshTimer = null;
    this.clearSseReconnectTimer();
    this.closeEventStream();
    this.liveConnectionMode = 'offline';
  }

  private connectEventStream(): void {
    this.closeEventStream();

    this.eventSource = new EventSource(`${this.apiBase}/events/stream`);
    this.eventSource.addEventListener('connected', () => {
      this.liveConnectionMode = 'push';
    });
    this.eventSource.addEventListener('transaction-scored', () => {
      this.handlePushEvent();
    });
    this.eventSource.addEventListener('bulk-upload', () => {
      this.handlePushEvent();
    });
    this.eventSource.onerror = () => {
      this.liveConnectionMode = this.liveUpdatesEnabled ? 'polling' : 'offline';
      this.closeEventStream();
      this.scheduleSseReconnect();
    };
  }

  private handlePushEvent(): void {
    if (!this.liveUpdatesEnabled || this.isSubmitting || this.isUploading) {
      return;
    }
    this.refreshDashboard();
  }

  private scheduleSseReconnect(): void {
    if (!this.liveUpdatesEnabled || this.sseReconnectTimer) {
      return;
    }

    this.sseReconnectTimer = setTimeout(() => {
      this.sseReconnectTimer = null;
      if (!this.liveUpdatesEnabled) {
        return;
      }
      this.connectEventStream();
    }, this.sseReconnectMs);
  }

  private closeEventStream(): void {
    if (!this.eventSource) {
      return;
    }

    this.eventSource.close();
    this.eventSource = null;
  }

  private clearSseReconnectTimer(): void {
    if (!this.sseReconnectTimer) {
      return;
    }

    clearTimeout(this.sseReconnectTimer);
    this.sseReconnectTimer = null;
  }
}
