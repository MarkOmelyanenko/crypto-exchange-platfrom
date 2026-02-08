import { useState, useEffect } from 'react';

/**
 * Last updated indicator with auto-updating relative time.
 * 
 * @param {Object} props
 * @param {Date|number|null} props.lastUpdatedAt - Timestamp of last update
 */
export function LastUpdatedIndicator({ lastUpdatedAt }) {
  const [relativeTime, setRelativeTime] = useState('');

  useEffect(() => {
    if (!lastUpdatedAt) {
      setRelativeTime('');
      return;
    }

    const updateRelativeTime = () => {
      const now = Date.now();
      const timestamp = lastUpdatedAt instanceof Date ? lastUpdatedAt.getTime() : lastUpdatedAt;
      const diffSeconds = Math.floor((now - timestamp) / 1000);

      let timeStr = '';
      if (diffSeconds < 60) {
        timeStr = `${diffSeconds}s ago`;
      } else if (diffSeconds < 3600) {
        const minutes = Math.floor(diffSeconds / 60);
        timeStr = `${minutes}m ago`;
      } else {
        const hours = Math.floor(diffSeconds / 3600);
        timeStr = `${hours}h ago`;
      }

      setRelativeTime(timeStr);
    };

    // Update immediately
    updateRelativeTime();

    // Update every 2 seconds
    const interval = setInterval(updateRelativeTime, 2000);

    return () => clearInterval(interval);
  }, [lastUpdatedAt]);

  if (!lastUpdatedAt) {
    return null;
  }

  const now = Date.now();
  const timestamp = lastUpdatedAt instanceof Date ? lastUpdatedAt.getTime() : lastUpdatedAt;
  const diffSeconds = Math.floor((now - timestamp) / 1000);

  // Determine status
  let status = 'Stale';
  let statusColor = '#ef4444'; // red
  if (diffSeconds < 15) {
    status = 'Live';
    statusColor = '#10b981'; // green
  } else if (diffSeconds < 60) {
    status = 'Delayed';
    statusColor = '#f59e0b'; // yellow
  }

  return (
    <div style={{
      display: 'flex',
      alignItems: 'center',
      gap: 6,
      fontSize: 11,
      color: '#6b7280',
      marginTop: 4,
      flexWrap: 'wrap',
    }}>
      <span>Last updated</span>
      <span style={{ color: '#9ca3af' }}>•</span>
      <span style={{
        display: 'flex',
        alignItems: 'center',
        gap: 4,
        color: statusColor,
        fontWeight: 500,
      }}>
        <span style={{
          width: 6,
          height: 6,
          borderRadius: '50%',
          backgroundColor: statusColor,
          display: 'inline-block',
        }} />
        {status}
      </span>
      <span style={{ color: '#9ca3af' }}>•</span>
      <span style={{ fontFamily: 'monospace' }}>updated {relativeTime}</span>
    </div>
  );
}

export default LastUpdatedIndicator;
