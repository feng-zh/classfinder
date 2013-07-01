package com.hp.it.gadsc.et.ei.tools.classfinder;

import java.io.Closeable;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;

import sun.jvmstat.monitor.HostIdentifier;
import sun.jvmstat.monitor.Monitor;
import sun.jvmstat.monitor.MonitorException;
import sun.jvmstat.monitor.MonitoredHost;
import sun.jvmstat.monitor.MonitoredVm;
import sun.jvmstat.monitor.VmIdentifier;

class VMAdapter implements Closeable, Callable<Properties> {
	private MonitoredVm vm;
	private MonitoredHost host;

	public VMAdapter(int id) throws Exception {
		try {
			host = MonitoredHost.getMonitoredHost(new HostIdentifier(
					(String) null));
			vm = host.getMonitoredVm(new VmIdentifier("//" + id + "?mode=r"));
		} catch (MonitorException e) {
			throw e;
		} catch (URISyntaxException ignored) {
		}
	}

	public VMAdapter(String stringInLine) throws Exception {
		try {
			host = MonitoredHost.getMonitoredHost(new HostIdentifier(
					(String) null));
			Set<?> vms = host.activeVms();
			for (Object vmId : vms) {
				MonitoredVm theVm = host.getMonitoredVm(new VmIdentifier("//"
						+ vmId + "?mode=r"));
				if (findValue(theVm, "sun.rt.javaCommand", stringInLine)) {
					vm = theVm;
					return;
				}
				if (findValue(theVm, "java.rt.vmArgs", stringInLine)) {
					vm = theVm;
					return;
				}
				if (findValue(theVm, "java.rt.vmFlags", stringInLine)) {
					vm = theVm;
					return;
				}
				if (findValue(theVm, "java.property.java.class.path",
						stringInLine)) {
					vm = theVm;
					return;
				}
				theVm.detach();
			}
			throw new MonitorException("cannot find vm with string: "
					+ stringInLine);
		} catch (MonitorException e) {
			throw e;
		} catch (URISyntaxException ignored) {
		}
	}

	private boolean findValue(MonitoredVm theVm, String string, String textFind)
			throws MonitorException {
		Monitor monitor = theVm.findByName(string);
		if (monitor != null) {
			return String.valueOf(monitor.getValue()).indexOf(textFind) >= 0;
		} else {
			return false;
		}
	}

	public void close() {
		if (vm != null && host != null) {
			try {
				host.detach(vm);
			} catch (MonitorException ignored) {
			}
			vm = null;
			host = null;
		}
	}

	public Properties call() throws Exception {
		List<?> list = vm.findByPattern(".*");
		Properties prop = new Properties();
		for (Object obj : list) {
			Monitor m = (Monitor) obj;
			prop.setProperty(m.getName(), String.valueOf(m.getValue()));
		}
		prop.setProperty("classfinder.pid",
				String.valueOf(vm.getVmIdentifier().getLocalVmId()));
		return prop;
	}

	public String findName(String name) throws Exception {
		Monitor monitor = vm.findByName(name);
		if (monitor != null) {
			return (String) monitor.getValue();
		} else {
			return null;
		}
	}
}