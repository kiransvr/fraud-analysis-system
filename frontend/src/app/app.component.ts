import { CommonModule } from '@angular/common';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
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

@Component({
  selector: 'app-root',
  imports: [CommonModule, FormsModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent implements OnInit {
  private readonly apiBase = environment.apiBaseUrl;

  transactions: TransactionResponse[] = [];
  alerts: AlertResponse[] = [];
  isLoading = false;
  isSubmitting = false;
  errorMessage = '';
  successMessage = '';

  transactionForm = {
    externalTransactionId: 'tx-ui-001',
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
  }

  refreshDashboard(): void {
    this.errorMessage = '';
    this.isLoading = true;

    const params = new HttpParams().set('limit', '20').set('offset', '0');

    this.http.get<TransactionResponse[]>(`${this.apiBase}/transactions`, { params }).subscribe({
      next: (transactions) => {
        this.transactions = transactions;
        this.isLoading = false;
      },
      error: (error: HttpErrorResponse) => {
        this.isLoading = false;
        this.errorMessage = this.extractError(error, 'Unable to load transactions.');
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
}
