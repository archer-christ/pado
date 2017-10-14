'''
Created on Sep 14, 2017

@author: dpark
'''
import json
import logging
import platform
import sys
import threading
import uuid

from paho.mqtt.client import MQTT_ERR_SUCCESS

from com.netcrest.pado.rpc.util import rpc_util
import paho.mqtt.client as mqtt


class RpcClient(object):
    '''
    RpcClient establishes RPC services via MQTT.
    
    RpcClient connects to the MQTT broker and subscribes its "reply"
    topic. It also provides methods to execute "request" services on the data
    node and the "result" method to send the result to the data node that
    originated the start of the client app which led to use of this class.
    '''
#     __metaclass__=Singleton
    topic_request = None
    topic_result = '/__pado/result'
    topic_reply = '/__pado/reply/' + uuid.uuid1().hex
    topic_agent_request = None
    is_agent = False
    thread = None
    is_terminated = False
    id_map = dict()
    id_rpc_listener_map = dict()
    live_id_rpc_listener_map = dict()
    client = None
    host = 'localhost'
    port = 1883
    logger = logging.getLogger('RpcClient')
    
    def __init__(self, server_id, host='localhost', port=1883, is_agent=False):
        '''
        Constructor
        '''
        self.topic_request = '/__pado/request/' + server_id
        self.topic_agent_request = '/__pado/request/agent/python/' + server_id
        self.host = host
        self.port = port
        self.is_agent = is_agent
        self.client = mqtt.Client()
        self.client.on_connect = self.__on_connect
        self.client.on_message = self.__on_message
        self.client.on_publish = self.__on_publish
        self.client.connect(host, port, 600)
        if is_agent:
            self.client.subscribe(self.topic_agent_request, 2)
        self.client.subscribe(self.topic_reply, 2)
        self.thread = threading.Thread(target=self.__run)
        self.thread.setDaemon(True)
        self.thread.start()
        print('RpcClient.__init__(): host=' + host + ", port=" + str(port) + ", server_id=" + server_id + ", is_agent=" + str(is_agent))
        sys.stdout.flush()
        
        
    # The callback for when the client receives a CONNACK response from the server.
    def __on_connect(self, client, userdata, flags, rc):
        print("RpcClient.__on_connect(): Connected with result code " + str(rc))
        sys.stdout.flush()
    
    # The callback for when a PUBLISH message is received from the server.
    def __on_message(self, client, userdata, msg):
        # A bug in Paho MQTT on Windows. The payload contains extra characters
        if platform.system() == 'Windows':
            message = str(msg.payload)[2:-1]
        else:
            message = str(msg.payload)
        if msg.topic == self.topic_agent_request:
            # agent request
            # TODO: use thread pool
            jrequest = json.loads(message)
            thread = self.AgentThread(jrequest)
            thread.start()
            
        else:
            # reply 
            jreply = json.loads(message)
            sys.stdout.flush()
            if 'id' in jreply:
                id_ = jreply['id']
                if id_ in self.id_map:
                    threadReply = self.id_map[id_]
                    if threadReply != None:
                        threadReply.__condition__.acquire()
                        threadReply.jreply = jreply
                        threadReply.__condition__.notify()
                        threadReply.__condition__.release()
            else:
                live_listener_set = self.getLiveRpcListenerSet(msg.topic)
                if live_listener_set != None:
                    for listener in live_listener_set:
                        listener(message)
    
    def __on_publish(self, client, userdata, mid):
        print("RpcClient.__on_publish(): mid=" + str(mid))
        sys.stdout.flush()
            
    def __run(self):
        while (self.is_terminated == False):
            self.client.loop(1.0)
    
    def close(self):
        self.is_terminated = True
        try:
            self.client.disconnect()
        except:
            sys.stderr.write('Unexpected error: ' + str(sys.exc_info()[0]))
            sys.stderr.flush()
    
    def is_closed(self):
        return self.is_terminated
    
    # Returns JSON reply
    def execute(self, rpc_context, jrequest, timeout = 0):
        '''Executes the specified request
        
        Args:
            rpc_context: RPC context object
            jrequest: JSON RPC 2.0 request
            timeout: Timeout in msec. Default is 0, i.e., no timeout
            
        Returns:
            JSON RPC 2.0 reply
        '''
        jreply = None
        threadReply = None
        id_ = jrequest['id']
        if id_ != None:
            threadReply = self.ThreadReply(threading.currentThread())
            self.id_map[id_] = threadReply
            jrequest['token'] = rpc_context.token
            jrequest['username'] = rpc_context.username
            jrequest['replytopic'] = self.topic_reply
            request = json.dumps(jrequest)
            (result, mid) = self.client.publish(self.topic_request, request, 2, False)
            if result != MQTT_ERR_SUCCESS:
                sys.stderr.write('rpc_client.execute() - publish filed')
                sys.stderr.flush()
                
            if timeout < 0:
                timeout = 0
            # convert to seconds
            timeout_in_sec = float(timeout/1000.0)
            threadReply.__condition__.acquire()
            while threadReply.jreply == None:
                threadReply.__condition__.wait(timeout_in_sec)
            threadReply.__condition__.release()
            jreply = threadReply.jreply    
        return jreply
    
    def __subscribe(self, topic):
        self.client.subscribe(topic, 2)
        
    def __unsubscribe(self, topic):
        self.client.unsubscribe(topic)
        
    def send_result(self, jresult):
        result = json.dumps(jresult)
        info = self.client.publish(self.topic_result, result, 2, False)
        info.wait_for_publish()
       
    def add_rpc_listener(self, listener_name, listener):
        if listener_name == None or len(listener_name) == 0 or listener == None:
            return
        topic = '/__pado/listener/' + listener_name
        if topic in self.id_rpc_listener_map:
            listener_set = self.id_rpc_listener_map[topic]
            if topic in listener_set:
                return
        else:
            listener_set = set()
            self.id_rpc_listener_map[topic] = listener_set
        self.__subscribe(topic)
        listener_set.add(listener)
        self.live_id_rpc_listener_map[topic] = set(listener_set)
        
    def remove_rpc_listener(self, listener_name, listener):
        if listener_name == None or len(listener_name) == 0 or listener == None:
            return
        topic = '/__pado/listener/' + listener_name
        if topic in self.id_rpc_listener_map:
            listener_set = self.id_rpc_listener_map[topic]
            if len(listener_set) == 0:
                self.__unsubscribe(topic)
            listener_set.remove(listener)
            self.live_id_rpc_listener_map[topic] = set(listener_set)
       
    def getLiveRpcListenerSet(self, topic):
        if topic == None or len(topic) == 0:
            return None
        if topic in self.live_id_rpc_listener_map:
            return self.live_id_rpc_listener_map[topic]
        else:
            return None
         
    class ThreadReply:
        thread = None
        jreply = None
        __condition__ = threading.Condition()
        def __init__(self, thread):
            self.thread = thread
    
    class AgentThread(threading.Thread):
        jrequest = None
        def __init__(self, jrequest):
            threading.Thread.__init__(self, name='RpcAgentThread')
            self.jrequest = jrequest
            
        def run(self):
            rpc_util.process_request(self.jrequest)
         
