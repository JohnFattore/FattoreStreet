import { FormEvent, useMemo, useState } from "react";
import {
  Alert,
  Badge,
  Button,
  Card,
  Col,
  Container,
  Form,
  Nav,
  Row,
} from "react-bootstrap";
import { useNavigate } from "react-router-dom";
import GenericLineChart from "../components/GenericLineChart";
import { SortableTable } from "../components/SortableTable";
import StateHandler from "../components/StateHandler";
import {
  useGetMonitoringJobsQuery,
  useGetMonitoringLogsQuery,
  useGetMonitoringMetricsQuery,
  useGetMonitoringSourcesQuery,
  useGetMonitoringSummaryQuery,
  useGetMonitoringTrafficQuery,
  useGetMonitoringUserActivityQuery,
} from "../functions/api";
import {
  IMonitoringApi4xx,
  IMonitoringErrorPath,
  IMonitoringJobStatus,
  IMonitoringLogEvent,
  IMonitoringServiceTotal,
  IMonitoringUserActivity,
} from "../interfaces";

const API_4XX_CARDS: {
  key: Exclude<keyof IMonitoringApi4xx, "total">;
  label: string;
}[] = [
  { key: "unauthorized", label: "401 Unauthorized" },
  { key: "forbidden", label: "403 Forbidden" },
  { key: "notFound", label: "404 Not Found" },
  { key: "other", label: "Other 4xx" },
];

type TimePreset = "15m" | "1h" | "6h" | "24h" | "7d" | "custom";

const PRESET_MINUTES: Record<Exclude<TimePreset, "custom">, number> = {
  "15m": 15,
  "1h": 60,
  "6h": 360,
  "24h": 1440,
  "7d": 10080,
};

function formatDuration(seconds: number | null): string {
  if (seconds == null) return "—";
  if (seconds < 60) return `${seconds}s`;
  const mins = Math.floor(seconds / 60);
  const rem = seconds % 60;
  return `${mins}m ${rem}s`;
}

function formatTimestamp(iso: string | null): string {
  if (!iso) return "Never";
  const parsed = new Date(iso);
  if (Number.isNaN(parsed.getTime())) return iso;
  return parsed.toLocaleString();
}

function getLevelBadgeVariant(
  level: IMonitoringLogEvent["level"],
): "danger" | "warning" | "info" | "secondary" {
  if (level === "ERROR") return "danger";
  if (level === "WARN") return "warning";
  if (level === "INFO") return "info";
  return "secondary";
}

function getJobBadge(job: IMonitoringJobStatus): {
  variant: "success" | "danger" | "warning" | "secondary";
  text: string;
} {
  if (job.status === "FAILED") {
    return { variant: "danger", text: "FAILED" };
  }
  if (job.isStale) {
    return { variant: "warning", text: "STALE" };
  }
  if (job.status === "SUCCESS") {
    return { variant: "success", text: "HEALTHY" };
  }
  return { variant: "secondary", text: "UNKNOWN" };
}

export function AdminDashboard() {
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState<"overview" | "logs">("overview");
  const [overviewHours, setOverviewHours] = useState<number>(24);
  const [autoRefresh, setAutoRefresh] = useState<boolean>(false);
  const [hideStaff, setHideStaff] = useState<boolean>(false);

  // Logs filter state
  const [selectedSource, setSelectedSource] = useState<string>("django");
  const [timePreset, setTimePreset] = useState<TimePreset>("1h");
  const [customStart, setCustomStart] = useState<string>("");
  const [customEnd, setCustomEnd] = useState<string>("");
  const [levelFilter, setLevelFilter] = useState<string>("");
  const [searchInput, setSearchInput] = useState<string>("");
  const [submittedSearch, setSubmittedSearch] = useState<string>("");
  const [nextToken, setNextToken] = useState<string>("");

  const overviewPollingInterval = autoRefresh ? 60_000 : 0;
  const logsPollingInterval = autoRefresh ? 30_000 : 0;

  const { data: sources } = useGetMonitoringSourcesQuery();
  const {
    data: jobsData,
    isLoading: jobsLoading,
    error: jobsError,
    refetch: refetchJobs,
  } = useGetMonitoringJobsQuery(undefined, {
    pollingInterval: overviewPollingInterval,
  });
  const {
    data: summaryData,
    isLoading: summaryLoading,
    error: summaryError,
    refetch: refetchSummary,
  } = useGetMonitoringSummaryQuery(
    { hours: overviewHours },
    { pollingInterval: overviewPollingInterval },
  );
  const {
    data: trafficData,
    isLoading: trafficLoading,
    error: trafficError,
    refetch: refetchTraffic,
  } = useGetMonitoringTrafficQuery(
    { hours: overviewHours },
    { pollingInterval: overviewPollingInterval },
  );
  const {
    data: metricsData,
    isLoading: metricsLoading,
    error: metricsError,
    refetch: refetchMetrics,
  } = useGetMonitoringMetricsQuery(
    { hours: overviewHours },
    { pollingInterval: overviewPollingInterval },
  );
  const {
    data: userActivityData,
    isLoading: userActivityLoading,
    error: userActivityError,
    refetch: refetchUserActivity,
  } = useGetMonitoringUserActivityQuery(
    { hours: overviewHours },
    { pollingInterval: overviewPollingInterval },
  );

  const logQueryWindow = useMemo(() => {
    if (timePreset === "custom") {
      return {
        start: customStart ? new Date(customStart).toISOString() : undefined,
        end: customEnd ? new Date(customEnd).toISOString() : undefined,
        minutes: undefined,
      };
    }
    return {
      start: undefined,
      end: undefined,
      minutes: PRESET_MINUTES[timePreset],
    };
  }, [timePreset, customStart, customEnd]);

  const {
    data: logsData,
    isLoading: logsLoading,
    error: logsError,
    refetch: refetchLogs,
  } = useGetMonitoringLogsQuery(
    {
      source: selectedSource,
      start: logQueryWindow.start,
      end: logQueryWindow.end,
      minutes: logQueryWindow.minutes,
      q: submittedSearch,
      level: levelFilter,
      nextToken: nextToken || undefined,
    },
    { pollingInterval: activeTab === "logs" ? logsPollingInterval : 0 },
  );

  const handleOpenSourceLogs = (sourceKey: string) => {
    setSelectedSource(sourceKey);
    setNextToken("");
    setActiveTab("logs");
  };

  const handleInspectUserLogs = (username: string) => {
    const query = `user=${username}`;
    setSelectedSource("django");
    setTimePreset(
      overviewHours <= 6 ? "6h" : overviewHours <= 24 ? "24h" : "7d",
    );
    setLevelFilter("");
    setSearchInput(query);
    setSubmittedSearch(query);
    setNextToken("");
    setActiveTab("logs");
  };

  const handleFilterSubmit = (event: FormEvent) => {
    event.preventDefault();
    setSubmittedSearch(searchInput.trim());
    setNextToken("");
  };

  const handleManualRefresh = () => {
    if (activeTab === "overview") {
      refetchJobs();
      refetchSummary();
      refetchTraffic();
      refetchMetrics();
      refetchUserActivity();
    } else {
      setNextToken("");
      refetchLogs();
    }
  };

  const visibleUserActivity = useMemo(
    () =>
      (userActivityData?.users ?? []).filter((u) => !hideStaff || !u.isStaff),
    [userActivityData, hideStaff],
  );

  const trafficChartData = useMemo(
    () =>
      (trafficData?.buckets ?? []).map((b) => ({
        date: b.bucket,
        totalRequests: b.totalRequests,
        status4xx: b.status4xx,
        status5xx: b.status5xx,
      })),
    [trafficData],
  );

  const metricsChartData = useMemo(
    () =>
      (metricsData?.series ?? []).map((pt) => ({
        date: pt.timestamp,
        cpuPercent: pt.cpuPercent,
        memUsedPercent: pt.memUsedPercent,
        diskRootPercent: pt.diskRootPercent,
        diskEbsPercent: pt.diskEbsPercent,
      })),
    [metricsData],
  );

  return (
    <Container className="py-3">
      <div className="d-flex justify-content-between align-items-center flex-wrap gap-2 mb-3">
        <div>
          <h2 className="mb-1">Admin Monitoring Dashboard</h2>
          <p className="text-muted mb-0">
            CloudWatch service logs, scheduled Fargate job health, Nginx
            traffic, per-user API activity and EC2 host capacity.
          </p>
        </div>
        <div className="d-flex align-items-center gap-2 flex-wrap">
          <Form.Check
            type="switch"
            id="monitoring-auto-refresh"
            label="Auto-refresh"
            checked={autoRefresh}
            onChange={(e) => setAutoRefresh(e.target.checked)}
          />
          <Button
            variant="outline-primary"
            size="sm"
            onClick={handleManualRefresh}
          >
            Refresh Now
          </Button>
          <Button
            variant="outline-secondary"
            size="sm"
            onClick={() => navigate("/admin/success-bar")}
          >
            Corporate Action Diagnostics
          </Button>
        </div>
      </div>

      <Nav
        variant="tabs"
        role="tablist"
        activeKey={activeTab}
        onSelect={(key) => setActiveTab(key === "logs" ? "logs" : "overview")}
        className="mb-4"
      >
        <Nav.Item>
          <Nav.Link eventKey="overview" role="tab">
            Overview
          </Nav.Link>
        </Nav.Item>
        <Nav.Item>
          <Nav.Link eventKey="logs" role="tab">
            Logs
          </Nav.Link>
        </Nav.Item>
      </Nav>

      {activeTab === "overview" ? (
        <>
          <div className="d-flex justify-content-between align-items-center mb-3 flex-wrap gap-2">
            <h4 className="mb-0">Scheduled Fargate Jobs</h4>
            <div className="d-flex align-items-center gap-2">
              <Form.Label htmlFor="overview-hours-select" className="mb-0">
                Window:
              </Form.Label>
              <Form.Select
                id="overview-hours-select"
                aria-label="Overview window"
                size="sm"
                value={overviewHours}
                onChange={(e) => setOverviewHours(Number(e.target.value))}
              >
                <option value={6}>Last 6 hours</option>
                <option value={24}>Last 24 hours</option>
                <option value={72}>Last 3 days</option>
                <option value={168}>Last 7 days</option>
              </Form.Select>
            </div>
          </div>

          <StateHandler
            isLoading={jobsLoading}
            errors={[jobsError]}
            content={
              <Row className="g-3 mb-4">
                {(jobsData?.jobs ?? []).map((job) => {
                  const badge = getJobBadge(job);
                  return (
                    <Col key={job.key} md={6} lg={4}>
                      <Card className="h-100">
                        <Card.Body className="d-flex flex-column justify-content-between">
                          <div>
                            <div className="d-flex justify-content-between align-items-start mb-2">
                              <Card.Title className="mb-0 fs-6">
                                {job.label}
                              </Card.Title>
                              <Badge bg={badge.variant}>{badge.text}</Badge>
                            </div>
                            <p className="text-muted small mb-2">
                              Schedule: {job.schedule}
                            </p>
                            <p className="small mb-1">
                              <strong>Last run:</strong>{" "}
                              {formatTimestamp(job.lastRunAt)}
                            </p>
                            <p className="small mb-1">
                              <strong>Duration:</strong>{" "}
                              {formatDuration(job.durationSeconds)} ·{" "}
                              <strong>Exit:</strong>{" "}
                              {job.exitCode != null ? job.exitCode : "—"}
                            </p>
                            {job.stoppedReason && (
                              <p className="text-muted small mb-2">
                                {job.stoppedReason}
                              </p>
                            )}
                          </div>
                          <div className="mt-2">
                            <Button
                              variant="outline-primary"
                              size="sm"
                              onClick={() => handleOpenSourceLogs(job.key)}
                            >
                              Inspect Job Logs
                            </Button>
                          </div>
                        </Card.Body>
                      </Card>
                    </Col>
                  );
                })}
              </Row>
            }
          />

          <h4 className="mb-3">
            Error &amp; Warning Summary (Last {overviewHours}h)
          </h4>
          <Card className="p-3 mb-4">
            <SortableTable<IMonitoringServiceTotal>
              data={summaryData?.totals ?? []}
              isLoading={summaryLoading}
              errors={[summaryError]}
              initialSortKey="errorCount"
              initialSortDirection="desc"
              columns={[
                {
                  label: "Service / Job",
                  sortKey: "label",
                },
                {
                  label: "Category",
                  sortKey: "category",
                },
                {
                  label: "Errors",
                  sortKey: "errorCount",
                  render: (row) => (
                    <Badge bg={row.errorCount > 0 ? "danger" : "secondary"}>
                      {row.errorCount}
                    </Badge>
                  ),
                },
                {
                  label: "Warnings",
                  sortKey: "warnCount",
                  render: (row) => (
                    <Badge bg={row.warnCount > 0 ? "warning" : "secondary"}>
                      {row.warnCount}
                    </Badge>
                  ),
                },
                {
                  label: "Logs",
                  sortKey: "source",
                  sortable: false,
                  render: (row) => (
                    <Button
                      variant="outline-primary"
                      size="sm"
                      onClick={() => handleOpenSourceLogs(row.source)}
                    >
                      Open Logs
                    </Button>
                  ),
                },
              ]}
            />
          </Card>

          <h4 className="mb-3">
            Nginx Traffic &amp; Error Responses (Last {overviewHours}h)
          </h4>
          <StateHandler
            isLoading={trafficLoading}
            errors={[trafficError]}
            content={
              <div className="mb-4">
                <Row className="g-3 mb-3">
                  <Col sm={4}>
                    <Card className="p-3 text-center">
                      <div className="text-muted small">Total Requests</div>
                      <div className="fs-4 fw-bold">
                        {trafficData?.totalRequests ?? 0}
                      </div>
                    </Card>
                  </Col>
                  <Col sm={4}>
                    <Card className="p-3 text-center">
                      <div className="text-muted small">4xx Client Errors</div>
                      <div className="fs-4 fw-bold">
                        {trafficData?.total4xx ?? 0}
                      </div>
                      <div className="text-muted small">
                        API {trafficData?.api4xx.total ?? 0} · Other{" "}
                        {trafficData?.nonApi4xx ?? 0}
                      </div>
                    </Card>
                  </Col>
                  <Col sm={4}>
                    <Card className="p-3 text-center">
                      <div className="text-muted small">5xx Server Errors</div>
                      <div className="fs-4 fw-bold">
                        <Badge
                          bg={
                            (trafficData?.total5xx ?? 0) > 0
                              ? "danger"
                              : "success"
                          }
                        >
                          {trafficData?.total5xx ?? 0}
                        </Badge>
                      </div>
                    </Card>
                  </Col>
                </Row>

                <h5>API Client Errors</h5>
                <p className="text-muted small">
                  4xx responses on /django/ and /springboot/ routes only. Other
                  4xx traffic is mostly bots probing paths the app never served.
                </p>
                <Row className="g-3 mb-3">
                  {API_4XX_CARDS.map(({ key, label }) => (
                    <Col key={key} xs={6} md={3}>
                      <Card className="p-3 text-center">
                        <div className="text-muted small">{label}</div>
                        <div className="fs-4 fw-bold">
                          {trafficData?.api4xx[key] ?? 0}
                        </div>
                      </Card>
                    </Col>
                  ))}
                </Row>

                {trafficChartData.length > 0 && (
                  <div className="mb-3">
                    <GenericLineChart
                      data={trafficChartData}
                      label="Hourly Request & Error Rates"
                      description="Hourly request volume, 4xx responses, and 5xx server errors parsed from nginx-logs."
                      latestReading=""
                      lines={[
                        {
                          dataKey: "totalRequests",
                          color: "var(--primary)",
                          name: "Requests",
                        },
                        {
                          dataKey: "status4xx",
                          color: "var(--secondary)",
                          name: "4xx",
                        },
                        {
                          dataKey: "status5xx",
                          color: "var(--tertiary)",
                          name: "5xx",
                        },
                      ]}
                    />
                  </div>
                )}

                {(trafficData?.topApi4xxPaths.length ?? 0) > 0 && (
                  <Card className="p-3 mb-3">
                    <h5>Top API 4xx Paths</h5>
                    <SortableTable<IMonitoringErrorPath>
                      data={trafficData?.topApi4xxPaths ?? []}
                      isLoading={false}
                      errors={[]}
                      initialSortKey="errorCount"
                      initialSortDirection="desc"
                      columns={[
                        { label: "Method", sortKey: "method" },
                        { label: "Path", sortKey: "path" },
                        { label: "Status", sortKey: "status" },
                        { label: "4xx Count", sortKey: "errorCount" },
                      ]}
                    />
                  </Card>
                )}

                {(trafficData?.top5xxPaths.length ?? 0) > 0 && (
                  <Card className="p-3">
                    <h5>Top 5xx Paths</h5>
                    <SortableTable<IMonitoringErrorPath>
                      data={trafficData?.top5xxPaths ?? []}
                      isLoading={false}
                      errors={[]}
                      initialSortKey="errorCount"
                      initialSortDirection="desc"
                      columns={[
                        { label: "Method", sortKey: "method" },
                        { label: "Path", sortKey: "path" },
                        { label: "Status", sortKey: "status" },
                        { label: "5xx Count", sortKey: "errorCount" },
                      ]}
                    />
                  </Card>
                )}
              </div>
            }
          />

          <div className="d-flex justify-content-between align-items-center mb-3 flex-wrap gap-2">
            <div>
              <h4 className="mb-1">User Activity (Last {overviewHours}h)</h4>
              <p className="text-muted small mb-0">
                Django API requests attributed to verified signed-in users
                (monitoring routes excluded; public Spring Boot routes are
                unauthenticated).
              </p>
            </div>
            <Form.Check
              type="switch"
              id="monitoring-hide-staff"
              label="Hide staff"
              checked={hideStaff}
              onChange={(e) => setHideStaff(e.target.checked)}
            />
          </div>
          <StateHandler
            isLoading={userActivityLoading}
            errors={[userActivityError]}
            content={
              <div className="mb-4">
                <Row className="g-3 mb-3">
                  <Col sm={4}>
                    <Card className="p-3 text-center">
                      <div className="text-muted small">
                        Active Signed-in Users
                      </div>
                      <div className="fs-4 fw-bold">
                        {userActivityData?.activeUsers ?? 0}
                      </div>
                    </Card>
                  </Col>
                  <Col sm={4}>
                    <Card className="p-3 text-center">
                      <div className="text-muted small">
                        Authenticated Requests
                      </div>
                      <div className="fs-4 fw-bold">
                        {userActivityData?.authenticatedRequests ?? 0}
                      </div>
                    </Card>
                  </Col>
                  <Col sm={4}>
                    <Card className="p-3 text-center">
                      <div className="text-muted small">Anonymous Requests</div>
                      <div className="fs-4 fw-bold">
                        {userActivityData?.anonymousRequests ?? 0}
                      </div>
                    </Card>
                  </Col>
                </Row>

                <Card className="p-3">
                  {visibleUserActivity.length === 0 ? (
                    <p className="text-muted mb-0">
                      No signed-in user activity recorded in the last{" "}
                      {overviewHours}h.
                    </p>
                  ) : (
                    <SortableTable<IMonitoringUserActivity>
                      data={visibleUserActivity}
                      isLoading={false}
                      errors={[]}
                      initialSortKey="requestCount"
                      initialSortDirection="desc"
                      columns={[
                        {
                          label: "User",
                          sortKey: "username",
                          render: (row) => (
                            <span>
                              {row.username}
                              {row.isStaff && (
                                <Badge bg="info" className="ms-2">
                                  Staff
                                </Badge>
                              )}
                            </span>
                          ),
                        },
                        {
                          label: "Requests",
                          sortKey: "requestCount",
                        },
                        {
                          label: "4xx",
                          sortKey: "status4xx",
                          render: (row) => (
                            <Badge
                              bg={row.status4xx > 0 ? "warning" : "secondary"}
                            >
                              {row.status4xx}
                            </Badge>
                          ),
                        },
                        {
                          label: "5xx",
                          sortKey: "status5xx",
                          render: (row) => (
                            <Badge
                              bg={row.status5xx > 0 ? "danger" : "secondary"}
                            >
                              {row.status5xx}
                            </Badge>
                          ),
                        },
                        {
                          label: "Feature Areas",
                          sortKey: "topApp",
                          render: (row) => (
                            <span className="small">
                              {row.apps
                                .slice(0, 3)
                                .map((a) => `${a.app} (${a.requestCount})`)
                                .join(", ")}
                            </span>
                          ),
                        },
                        {
                          label: "Last Active",
                          sortKey: "lastSeenMs",
                          render: (row) => (
                            <span className="text-nowrap small">
                              {formatTimestamp(row.lastSeen)}
                            </span>
                          ),
                        },
                        {
                          label: "Logs",
                          sortKey: "username",
                          sortable: false,
                          render: (row) => (
                            <Button
                              variant="outline-primary"
                              size="sm"
                              onClick={() =>
                                handleInspectUserLogs(row.username)
                              }
                            >
                              Inspect Logs
                            </Button>
                          ),
                        },
                      ]}
                    />
                  )}
                </Card>
              </div>
            }
          />

          <h4 className="mb-3">EC2 Host Metrics (Last {overviewHours}h)</h4>
          <StateHandler
            isLoading={metricsLoading}
            errors={[metricsError]}
            content={
              !metricsData?.configured ? (
                <Alert variant="info">
                  Host metrics are not configured yet. Set{" "}
                  <code>MONITORING_INSTANCE_ID</code> in the{" "}
                  <code>fattorestreet/env</code> secret to display EC2 CPU,
                  memory, and disk utilization.
                </Alert>
              ) : (
                <div className="mb-4">
                  <Row className="g-3 mb-3">
                    <Col sm={6} md={3}>
                      <Card className="p-3 text-center">
                        <div className="text-muted small">CPU Utilization</div>
                        <div className="fs-4 fw-bold">
                          {metricsData.latest.cpuPercent != null
                            ? `${metricsData.latest.cpuPercent}%`
                            : "—"}
                        </div>
                      </Card>
                    </Col>
                    <Col sm={6} md={3}>
                      <Card className="p-3 text-center">
                        <div className="text-muted small">Memory Used</div>
                        <div className="fs-4 fw-bold">
                          {metricsData.latest.memUsedPercent != null
                            ? `${metricsData.latest.memUsedPercent}%`
                            : "—"}
                        </div>
                      </Card>
                    </Col>
                    <Col sm={6} md={3}>
                      <Card className="p-3 text-center">
                        <div className="text-muted small">Root Disk (/)</div>
                        <div className="fs-4 fw-bold">
                          {metricsData.latest.diskRootPercent != null
                            ? `${metricsData.latest.diskRootPercent}%`
                            : "—"}
                        </div>
                      </Card>
                    </Col>
                    <Col sm={6} md={3}>
                      <Card className="p-3 text-center">
                        <div className="text-muted small">
                          EBS Disk (/mnt/ebs)
                        </div>
                        <div className="fs-4 fw-bold">
                          {metricsData.latest.diskEbsPercent != null
                            ? `${metricsData.latest.diskEbsPercent}%`
                            : "—"}
                        </div>
                      </Card>
                    </Col>
                  </Row>

                  {metricsChartData.length > 0 && (
                    <GenericLineChart
                      data={metricsChartData}
                      label={`EC2 Capacity (${metricsData.instanceId})`}
                      description="CPU from AWS/EC2 and memory/disk utilization from the CloudWatch Agent."
                      latestReading=""
                      lines={[
                        {
                          dataKey: "cpuPercent",
                          color: "var(--primary)",
                          name: "CPU %",
                        },
                        {
                          dataKey: "memUsedPercent",
                          color: "var(--secondary)",
                          name: "Memory %",
                        },
                        {
                          dataKey: "diskRootPercent",
                          color: "var(--tertiary)",
                          name: "Disk / %",
                        },
                        {
                          dataKey: "diskEbsPercent",
                          color: "var(--quaternary)",
                          name: "Disk /mnt/ebs %",
                        },
                      ]}
                    />
                  )}
                </div>
              )
            }
          />
        </>
      ) : (
        <div>
          <Card className="p-3 mb-3">
            <Form onSubmit={handleFilterSubmit}>
              <Row className="g-2 align-items-end">
                <Col md={3}>
                  <Form.Group controlId="log-source-select">
                    <Form.Label>Log Source</Form.Label>
                    <Form.Select
                      value={selectedSource}
                      onChange={(e) => {
                        setSelectedSource(e.target.value);
                        setNextToken("");
                      }}
                    >
                      {(sources ?? []).map((src) => (
                        <option key={src.key} value={src.key}>
                          {src.label} ({src.logGroup})
                        </option>
                      ))}
                      {(!sources || sources.length === 0) && (
                        <option value="django">Django API</option>
                      )}
                    </Form.Select>
                  </Form.Group>
                </Col>

                <Col md={2}>
                  <Form.Group controlId="log-time-preset">
                    <Form.Label>Time Range</Form.Label>
                    <Form.Select
                      value={timePreset}
                      onChange={(e) => {
                        setTimePreset(e.target.value as TimePreset);
                        setNextToken("");
                      }}
                    >
                      <option value="15m">Last 15 minutes</option>
                      <option value="1h">Last 1 hour</option>
                      <option value="6h">Last 6 hours</option>
                      <option value="24h">Last 24 hours</option>
                      <option value="7d">Last 7 days</option>
                      <option value="custom">Custom range</option>
                    </Form.Select>
                  </Form.Group>
                </Col>

                <Col md={2}>
                  <Form.Group controlId="log-level-select">
                    <Form.Label>Level</Form.Label>
                    <Form.Select
                      value={levelFilter}
                      onChange={(e) => {
                        setLevelFilter(e.target.value);
                        setNextToken("");
                      }}
                    >
                      <option value="">All Levels</option>
                      <option value="ERROR">ERROR</option>
                      <option value="WARN">WARN</option>
                      <option value="INFO">INFO</option>
                      <option value="DEBUG">DEBUG</option>
                    </Form.Select>
                  </Form.Group>
                </Col>

                <Col md={3}>
                  <Form.Group controlId="log-search-input">
                    <Form.Label>Search Text</Form.Label>
                    <Form.Control
                      type="text"
                      placeholder="Filter message text..."
                      value={searchInput}
                      onChange={(e) => setSearchInput(e.target.value)}
                    />
                  </Form.Group>
                </Col>

                <Col md={2} className="d-grid">
                  <Button type="submit" variant="primary">
                    Apply Filter
                  </Button>
                </Col>
              </Row>

              {timePreset === "custom" && (
                <Row className="g-2 mt-2">
                  <Col md={4}>
                    <Form.Group controlId="log-custom-start">
                      <Form.Label>Start (local time)</Form.Label>
                      <Form.Control
                        type="datetime-local"
                        value={customStart}
                        onChange={(e) => {
                          setCustomStart(e.target.value);
                          setNextToken("");
                        }}
                      />
                    </Form.Group>
                  </Col>
                  <Col md={4}>
                    <Form.Group controlId="log-custom-end">
                      <Form.Label>End (local time)</Form.Label>
                      <Form.Control
                        type="datetime-local"
                        value={customEnd}
                        onChange={(e) => {
                          setCustomEnd(e.target.value);
                          setNextToken("");
                        }}
                      />
                    </Form.Group>
                  </Col>
                </Row>
              )}
            </Form>
          </Card>

          <Card className="p-3">
            <div className="d-flex justify-content-between align-items-center mb-2 flex-wrap gap-2">
              <div className="text-muted small">
                Showing {logsData?.events.length ?? 0} events from{" "}
                <code>{logsData?.logGroup ?? selectedSource}</code>
              </div>
              <div className="d-flex gap-2">
                {nextToken && (
                  <Button
                    variant="outline-secondary"
                    size="sm"
                    onClick={() => setNextToken("")}
                  >
                    Back to First Page
                  </Button>
                )}
                {logsData?.nextToken && (
                  <Button
                    variant="outline-primary"
                    size="sm"
                    onClick={() => setNextToken(logsData.nextToken ?? "")}
                  >
                    Load More Events
                  </Button>
                )}
              </div>
            </div>

            <SortableTable<IMonitoringLogEvent>
              data={logsData?.events ?? []}
              isLoading={logsLoading}
              errors={[logsError]}
              initialSortKey="timestampMs"
              initialSortDirection="desc"
              columns={[
                {
                  label: "Time",
                  sortKey: "timestampMs",
                  render: (row) => (
                    <span className="text-nowrap small">
                      {formatTimestamp(row.timestamp)}
                    </span>
                  ),
                },
                {
                  label: "Level",
                  sortKey: "level",
                  render: (row) => (
                    <Badge bg={getLevelBadgeVariant(row.level)}>
                      {row.level}
                    </Badge>
                  ),
                },
                {
                  label: "Stream",
                  sortKey: "logStream",
                  render: (row) => (
                    <span className="small text-muted">{row.logStream}</span>
                  ),
                },
                {
                  label: "Message",
                  sortKey: "message",
                  render: (row) => (
                    <code className="text-break">{row.message}</code>
                  ),
                },
              ]}
            />
          </Card>
        </div>
      )}
    </Container>
  );
}
