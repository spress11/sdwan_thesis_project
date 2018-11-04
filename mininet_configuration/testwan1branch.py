
from mininet.net import Mininet
from mininet.node import RemoteController
from mininet.node import OVSSwitch
from mininet.link import TCLink
from mininet.link import TCIntf
from mininet.cli import CLI
from mininet.log import setLogLevel, info
import subprocess

def WanNet():

    #Create an empty network and add nodes to it.
    net = Mininet( controller=RemoteController, link=TCLink, intf=TCIntf, autoSetMacs=True )

    info( '*** Adding controller\n' )
    c0 = net.addController( 'c0' )

    info( '*** Adding hosts\n' )
    h1 = net.addHost( 'h1', ip = '10.0.1.1')
    h2 = net.addHost( 'h2', ip = '10.0.0.2' )

    info( '*** Adding switches\n' )
    s1 = net.addSwitch( 's1' )
    s2 = net.addSwitch( 's2' )
    s3 = net.addSwitch( 's3' )
    s4 = net.addSwitch( 's4' )

    info( '*** Creating links\n' )
    # Add links from gateway switch to combiner switches
    net.addLink( s1, s2, port1=1, port2=1, bw=10 )
    net.addLink( s1, s3, port1=2, port2=1, bw=20 )
    net.addLink( s2, s4, port1=2, port2=1 )
    net.addLink( s3, s4, port1=2, port2=2 )

    # Add link from host to gateway
    net.addLink( h2, s1 )

    # Add link from combiner switch to internet host
    net.addLink( s4, h1 )

    info( '*** Starting network\n')
    net.build()

    info( '*** Starting controllers\n')
    c0.start()

    info( '*** Starting switches\n')
    net.get('s4').start([])
    net.get('s2').start([])
    net.get('s1').start([c0])
    net.get('s3').start([])

    info( '*** Running CLI\n' )
    CLI( net )

    info( '*** Stopping network' )
    net.stop()

if __name__ == '__main__':
    setLogLevel( 'info' )
    WanNet()
